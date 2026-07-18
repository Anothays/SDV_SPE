#!/bin/sh
# Exécute un plan JMeter choisi via TEST_PLAN (défaut : profil-api-load-test.jmx).
# Résultats et rapports nommés d'après le plan pour ne pas s'écraser entre eux.
set -e

TEST_PLAN="${TEST_PLAN:-profil-api-load-test.jmx}"
NAME="${TEST_PLAN%.jmx}"
RESULT="/results/${NAME}-result.jtl"
REPORT="/reports/${NAME}"

if [ ! -f "/test-plans/${TEST_PLAN}" ]; then
    echo "ERREUR : plan introuvable : /test-plans/${TEST_PLAN}" >&2
    exit 1
fi

echo "=== JMeter : ${TEST_PLAN} ==="
rm -f "${RESULT}"
rm -rf "${REPORT}"

jmeter -n \
    -t "/test-plans/${TEST_PLAN}" \
    -l "${RESULT}" \
    -e -o "${REPORT}"

echo "=== Terminé : résultat ${RESULT} · rapport HTML ${REPORT}/index.html ==="