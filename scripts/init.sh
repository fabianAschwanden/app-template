#!/usr/bin/env bash
# Initialisiert eine neue App aus diesem Template:
#   ./scripts/init.sh <app-name> <base-package>
# Beispiel:
#   ./scripts/init.sh psp ch.css.psp
set -euo pipefail

APP_NAME="${1:?Usage: ./scripts/init.sh <app-name> <base-package>  (z.B. psp ch.css.psp)}"
BASE_PACKAGE="${2:?Usage: ./scripts/init.sh <app-name> <base-package>  (z.B. psp ch.css.psp)}"

OLD_PACKAGE="ch.example.app"
OLD_NAME="app-template"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "App-Name:     $OLD_NAME -> $APP_NAME"
echo "Base-Package: $OLD_PACKAGE -> $BASE_PACKAGE"

# 1) Java-Quellen in neue Paketstruktur verschieben
OLD_PATH="${OLD_PACKAGE//.//}"
NEW_PATH="${BASE_PACKAGE//.//}"
for SRC_ROOT in src/main/java src/test/java; do
  mkdir -p "$SRC_ROOT/$NEW_PATH"
  cp -R "$SRC_ROOT/$OLD_PATH/." "$SRC_ROOT/$NEW_PATH/"
  # alte Wurzel entfernen (nur die Segmente des alten Pakets)
  rm -rf "$SRC_ROOT/${OLD_PATH%%/*}"
done

# 2) Referenzen ersetzen (Paket, Artefakt-/App-Name)
GROUP_ID="${BASE_PACKAGE%.*}"
grep -rl --exclude-dir=node_modules --exclude-dir=target --exclude-dir=.git "$OLD_PACKAGE" . \
  | xargs sed -i.bak "s/${OLD_PACKAGE//./\\.}/$BASE_PACKAGE/g"
grep -rl --exclude-dir=node_modules --exclude-dir=target --exclude-dir=.git "$OLD_NAME" . \
  | grep -v 'scripts/init.sh' \
  | xargs sed -i.bak "s/$OLD_NAME/$APP_NAME/g"
sed -i.bak "s|<groupId>ch.example</groupId>|<groupId>$GROUP_ID</groupId>|" pom.xml
find . -name '*.bak' -not -path './node_modules/*' -delete

echo
echo "Fertig. Nächste Schritte:"
echo "  1. Beispiel-Durchstich 'Note' durch echte Fachlichkeit ersetzen (Suche nach 'Note')."
echo "  2. README.md auf die neue App anpassen, dieses Script löschen."
echo "  3. ./mvnw verify"
