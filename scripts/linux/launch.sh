#!/bin/sh
# A loader failure can prevent the JVM from starting, so diagnostics live outside Java.
# The native launcher stays in bin with a matching .bin.cfg to preserve jpackage paths.
# Successful runs leave no extra logs; failures are retained outside the installation.
bin_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd) || exit 1
native="$bin_dir/TheSoundboard.bin"
raw=$(mktemp "${TMPDIR:-/tmp}/thesoundboard-startup.XXXXXX") || exit 1
trap 'rm -f -- "$raw"' EXIT
"$native" "$@" 2>"$raw"
status=$?
if [ "$status" -eq 0 ]; then cat "$raw" >&2; exit 0; fi
case "${LC_ALL:-${LC_MESSAGES:-${LANG:-}}}" in ru*) russian=true ;; *) russian=false ;; esac
glibc=$(grep -Eo 'GLIBC_[0-9]+(\.[0-9]+)+' "$raw" | sort -Vu | tail -n 1 | sed 's/GLIBC_//')
glibcxx=$(grep -Eo 'GLIBCXX_[0-9]+(\.[0-9]+)+' "$raw" | sort -Vu | tail -n 1 | sed 's/GLIBCXX_//')
missing=$(grep -Eo '[[:alnum:]_+.-]+\.so(\.[0-9]+)*: cannot open shared object file' "$raw" | sed 's/:.*//' | sort -u)
if [ -n "$glibc" ]; then
    if "$russian"; then message="Требуется glibc $glibc или новее. Обновите дистрибутив Linux до версии с этой glibc."
    else message="glibc $glibc or newer is required. Upgrade to a Linux distribution that includes this version."; fi
elif [ -n "$glibcxx" ]; then
    if "$russian"; then message="Системная libstdc++ не поддерживает GLIBCXX_$glibcxx. Обновите libstdc++ через менеджер пакетов вашей системы."
    else message="The system libstdc++ does not provide GLIBCXX_$glibcxx. Update libstdc++ using your system package manager."; fi
elif [ -n "$missing" ]; then
    if "$russian"; then message="Не удалось загрузить библиотеки:
$missing
Установите системные пакеты, предоставляющие эти библиотеки. Если файл входит в TheSoundboard, заново распакуйте полный архив приложения."
    else message="Cannot load libraries:
$missing
Install the system packages providing these libraries. If the file belongs to TheSoundboard, extract the complete application archive again."; fi
elif grep -Eq 'Missing GStreamer|Invalid GStreamer .*manifest entry|Cannot load GStreamer plugin|manifest contains no audio plugins' "$raw" || [ ! -x "$native" ]; then
    if "$russian"; then message="Файлы приложения отсутствуют или повреждены. Заново распакуйте полный архив TheSoundboard."
    else message="Application files are missing or damaged. Extract the complete TheSoundboard archive again."; fi
elif grep -Eq 'Unable to open DISPLAY|cannot open display' "$raw"; then
    if "$russian"; then message="Нет подключения к графическому сеансу. Запустите приложение в рабочем столе Linux."
    else message="No graphical session is available. Run the application from a Linux desktop session."; fi
elif grep -Eq 'Unable to load glass GTK library|no glass in java.library.path' "$raw"; then
    if "$russian"; then message="Не удалось загрузить GTK. Установите системные библиотеки GTK 3 и их зависимости."
    else message="Cannot load GTK. Install the system GTK 3 libraries and their dependencies."; fi
else
    if "$russian"; then message="Не удалось запустить TheSoundboard. Технические подробности приведены ниже."
    else message="TheSoundboard could not start. Technical details are provided below."; fi
fi
log_dir="${HOME:-${TMPDIR:-/tmp}}/.thesoundboard/logs"
log="$raw"
if mkdir -p -- "$log_dir" 2>/dev/null; then
    candidate=$(mktemp "$log_dir/startup-XXXXXX.log" 2>/dev/null) && log="$candidate"
fi
# Read first because the temporary file is also the fallback if the user log folder is unwritable.
details=$(cat "$raw")
{ printf '%s\n\n' "$message"; printf '%s\n' "$details"; } >"$log"
printf '%s\n\n%s\n' "$message" "Diagnostics: $log" >&2
if [ "$log" = "$raw" ]; then trap - EXIT; fi
# The pre-JVM fallback cannot use application skins; text-only desktop dialogs avoid stock error icons.
if [ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]; then
    summary=$(mktemp "${TMPDIR:-/tmp}/thesoundboard-message.XXXXXX") || exit "$status"
    printf '%s\n\n%s\n' "$message" "Diagnostics: $log" >"$summary"
    if command -v zenity >/dev/null 2>&1; then
        zenity --text-info --title="The Soundboard" --filename="$summary" --width=640 --height=320 2>/dev/null || :
    elif command -v kdialog >/dev/null 2>&1; then
        kdialog --title "The Soundboard" --textbox "$summary" 640 320 2>/dev/null || :
    elif command -v xmessage >/dev/null 2>&1; then
        xmessage -center -title "The Soundboard" -file "$summary" 2>/dev/null || :
    fi
    rm -f -- "$summary"
fi
exit "$status"
