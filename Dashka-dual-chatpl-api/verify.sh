# =========================================================
# DASHKA VERIFY SCRIPT
# Сравнение двух .tar.gz архивов проекта
# Цель:
# - проверить что структура совпадает
# - найти новые файлы
# - убедиться что старые файлы не потеряны
# - сравнить содержимое файлов
# =========================================================

#!/bin/bash

set -e

OLD_ARCHIVE="dashka-dual-chatpl-api.tar.gz"
NEW_ARCHIVE="dashka-dual-chatpl-api-rec-001-005.tar.gz"

OLD_DIR="old_version"
NEW_DIR="new_version"

echo "========================================="
echo "DASHKA FILE VERIFY START"
echo "========================================="

# Очистка
rm -rf "$OLD_DIR"
rm -rf "$NEW_DIR"

mkdir "$OLD_DIR"
mkdir "$NEW_DIR"

echo ""
echo "[1/6] Распаковка архивов..."
tar -xzf "$OLD_ARCHIVE" -C "$OLD_DIR"
tar -xzf "$NEW_ARCHIVE" -C "$NEW_DIR"

echo ""
echo "[2/6] Сравнение структуры файлов..."
find "$OLD_DIR" -type f | sed "s|$OLD_DIR/||" | sort > old_files.txt
find "$NEW_DIR" -type f | sed "s|$NEW_DIR/||" | sort > new_files.txt

echo ""
echo "=== ФАЙЛЫ ТОЛЬКО В NEW ==="
comm -13 old_files.txt new_files.txt || true

echo ""
echo "=== ФАЙЛЫ ТОЛЬКО В OLD ==="
comm -23 old_files.txt new_files.txt || true

echo ""
echo "[3/6] Проверка изменений содержимого..."
diff -rq "$OLD_DIR" "$NEW_DIR" > diff_report.txt || true

echo ""
echo "=== ИЗМЕНЕННЫЕ ФАЙЛЫ ==="
cat diff_report.txt

echo ""
echo "[4/6] Подсчет файлов..."

OLD_COUNT=$(find "$OLD_DIR" -type f | wc -l)
NEW_COUNT=$(find "$NEW_DIR" -type f | wc -l)

echo "OLD FILES: $OLD_COUNT"
echo "NEW FILES: $NEW_COUNT"

echo ""
echo "[5/6] Проверка checksum одинаковых файлов..."

while read file; do
    if [ -f "$NEW_DIR/$file" ]; then

        OLD_HASH=$(shasum "$OLD_DIR/$file" | awk '{print $1}')
        NEW_HASH=$(shasum "$NEW_DIR/$file" | awk '{print $1}')

        if [ "$OLD_HASH" != "$NEW_HASH" ]; then
            echo "CHANGED: $file"
        fi
    fi
done < old_files.txt

echo ""
echo "[6/6] Готово"

echo ""
echo "========================================="
echo "VERIFY COMPLETE"
echo "========================================="

echo ""
echo "Файлы отчетов:"
echo "- old_files.txt"
echo "- new_files.txt"
echo "- diff_report.txt"