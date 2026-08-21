#!/bin/bash
# Create the Flyway account used only by admin-app (design §6.9 / 14-deployment §3.1).
set -euo pipefail
mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<EOSQL
CREATE USER IF NOT EXISTS '${MKT_FLYWAY_USER}'@'%' IDENTIFIED BY '${MKT_FLYWAY_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${MKT_FLYWAY_USER}'@'%';
FLUSH PRIVILEGES;
EOSQL
