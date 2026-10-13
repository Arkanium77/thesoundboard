"""Assemble an experimental audio-only runtime from pinned ready-built binaries.

Exact files are selected instead of copying a full multimedia distribution. Archives
are verified before opening; no native compiler or system package installation is used.
Linux dpkg-deb extraction and stripping are confined to the build workspace.
"""

import argparse
import base64
import hashlib
import json
import pathlib
import platform
import shutil
import subprocess
import tempfile
import tarfile
import urllib.request
import zipfile


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def inside(root, name):
    path = root / pathlib.PurePosixPath(name)
    if root.resolve() not in path.resolve().parents:
        raise ValueError("Runtime path escapes its workspace: " + name)
    return path


def fetch(item, downloads):
    if pathlib.PurePosixPath(item["name"]).name != item["name"]:
        raise ValueError("Invalid archive filename")
    path = downloads / item["name"]
    if not path.is_file() or digest(path) != item["sha256"]:
        temporary = path.with_suffix(path.suffix + ".part")
        failures = []
        for url in [item["url"], *item.get("mirrors", [])]:
            try:
                print("Downloading " + item["name"], flush=True)
                request = urllib.request.Request(url, headers={"User-Agent": "TheSoundboard-native-builder"})
                with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
                    shutil.copyfileobj(response, output)
                if digest(temporary) != item["sha256"]:
                    raise ValueError("Native archive SHA256 mismatch: " + item["name"])
                break
            except Exception as error:
                failures.append(str(error))
                temporary.unlink(missing_ok=True)
        else:
            raise RuntimeError("Cannot obtain verified archive " + item["name"] + ": " + "; ".join(failures))
        temporary.replace(path)
    return path


def zip_content(archive, entry):
    # Resolve SONAME symlinks inside the archive, never on the host filesystem.
    visited = set()
    while True:
        if entry in visited or entry.startswith("/") or ".." in pathlib.PurePosixPath(entry).parts:
            raise ValueError("Invalid native archive symlink: " + entry)
        visited.add(entry)
        info = archive.getinfo(entry)
        data = archive.read(info)
        if (info.external_attr >> 16) & 0xf000 != 0xa000:
            return data
        target = pathlib.PurePosixPath(entry).parent / data.decode("utf-8")
        parts = []
        for part in target.parts:
            if part == "..":
                if not parts:
                    raise ValueError("Native symlink escapes archive")
                parts.pop()
            elif part != ".":
                parts.append(part)
        entry = "/".join(parts)


def prepare(catalog, output, downloads):
    if output.exists() and any(output.iterdir()) and not (output / "runtime-manifest.json").is_file():
        raise ValueError("Refusing to replace a directory not created by this builder: " + str(output))
    downloads.mkdir(parents=True, exist_ok=True)
    archives = {item["name"]: fetch(item, downloads) for item in catalog["archives"]}
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="gstreamer-", dir=output.parent) as directory:
        workspace = pathlib.Path(directory)
        staged = workspace / "runtime"
        staged.mkdir()
        extracted = {}
        manifest = []
        for item in catalog["files"]:
            archive_path = archives[item["archive"]]
            destination = inside(staged, item["target"])
            destination.parent.mkdir(parents=True, exist_ok=True)
            if archive_path.suffix == ".deb":
                if item["archive"] not in extracted:
                    package = workspace / (item["archive"] + ".unpacked")
                    subprocess.run(["dpkg-deb", "-x", str(archive_path), str(package)], check=True)
                    extracted[item["archive"]] = package
                source = inside(extracted[item["archive"]], item["entry"])
                shutil.copyfile(source, destination)
            elif archive_path.suffix == ".conda":
                # Conda's package is a zip of zstd tar streams. Read only pinned regular members; never extract all.
                from compression import zstd
                import io
                with zipfile.ZipFile(archive_path) as archive:
                    payload = item["payload"]
                    with zstd.open(io.BytesIO(archive.read(payload))) as source, tarfile.open(fileobj=source, mode="r|") as package:
                        member = next((entry for entry in package if entry.name == item["entry"]), None)
                        if member is None or not member.isfile():
                            raise ValueError("Expected a regular conda runtime file: " + item["entry"])
                        with package.extractfile(member) as content, destination.open("wb") as stream:
                            shutil.copyfileobj(content, stream)
            elif archive_path.suffix in (".zip", ".whl", ".jar"):
                with zipfile.ZipFile(archive_path) as archive:
                    destination.write_bytes(zip_content(archive, item["entry"]))
            elif archive_path.name.endswith((".tar.gz", ".tar.xz", ".tar.bz2")):
                with tarfile.open(archive_path) as archive:
                    member = archive.getmember(item["entry"])
                    if not member.isfile():
                        raise ValueError("Expected a regular license file")
                    with archive.extractfile(member) as source, destination.open("wb") as stream:
                        shutil.copyfileobj(source, stream)
            else:
                if item.get("encoding") == "base64":
                    # Gitiles serves pinned source notices as base64; verify the original download first.
                    destination.write_bytes(base64.b64decode(archive_path.read_bytes(), validate=True))
                else:
                    shutil.copyfile(archive_path, destination)
            if item.get("strip"):
                subprocess.run(["strip", "--strip-unneeded", str(destination)], check=True)
            if destination.suffix in (".dll", ".dylib") or ".so" in destination.name:
                manifest.append({"file": item["target"], "sha256": digest(destination),
                                 "bytes": destination.stat().st_size})
        if catalog.get("preloadMode") == "elf":
            # Ready-built libraries are loaded in DT_NEEDED order; the application needs no LD_LIBRARY_PATH.
            import re
            chosen = {pathlib.PurePosixPath(item["file"]).name: item["file"] for item in manifest
                      if not item["file"].startswith("lib/gstreamer-1.0/")}
            graph = {}
            for name, filename in chosen.items():
                metadata = subprocess.check_output(["readelf", "-d", str(staged / filename)], text=True)
                graph[name] = re.findall(r"\(NEEDED\).*?\[(.*?)\]", metadata)
            ordered, visited = [], set()
            def visit(name):
                if name not in chosen or name in visited:
                    return
                visited.add(name)
                for dependency in graph[name]:
                    visit(dependency)
                ordered.append(chosen[name])
            for name in sorted(chosen):
                visit(name)
            (staged / "native-load-order.txt").write_text("\n".join(ordered) + "\n", encoding="utf-8")
        if catalog.get("preload"):
            (staged / "native-load-order.txt").write_text("\n".join(catalog["preload"]) + "\n", encoding="utf-8")
        record = {key: value for key, value in catalog.items() if key not in ("files", "preload")}
        record["files"] = manifest
        if catalog.get("notice"):
            notices = staged / "licenses"
            notices.mkdir(exist_ok=True)
            (notices / "NOTICE.txt").write_text(catalog["notice"] + "\n", encoding="utf-8")
        (staged / "runtime-manifest.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
        # Never merge: an obsolete plugin could silently change formats or licensing.
        if output.exists():
            shutil.rmtree(output)
        staged.rename(output)
    print("Prepared " + str(output) + ": " + str(sum(item["bytes"] for item in manifest)) + " native bytes", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--platform", choices=("windows-x64", "linux-x64", "macos-x64", "macos-arm64"))
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--downloads", type=pathlib.Path)
    args = parser.parse_args()
    target = args.platform
    if target is None:
        system = {"Windows": "windows", "Linux": "linux", "Darwin": "macos"}[platform.system()]
        architecture = {"AMD64": "x64", "x86_64": "x64", "arm64": "arm64", "aarch64": "arm64"}[platform.machine()]
        target = system + "-" + architecture
    name = "macos-universal" if target.startswith("macos-") else target
    catalog = json.loads((pathlib.Path(__file__).parent / (name + ".json")).read_text(encoding="utf-8"))
    output = args.output.resolve()
    if output == pathlib.Path(output.anchor) or len(output.parts) < 3:
        raise ValueError("Native output must be a dedicated build directory")
    downloads = args.downloads.resolve() if args.downloads else output.parent / "downloads"
    if downloads == output or output in downloads.parents:
        raise ValueError("Downloads must be outside the replaceable native output")
    prepare(catalog, output, downloads)


if __name__ == "__main__":
    main()
