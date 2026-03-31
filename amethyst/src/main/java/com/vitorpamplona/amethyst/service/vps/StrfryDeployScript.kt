/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.vps

object StrfryDeployScript {
    private const val D = "$"

    fun generate(
        relayName: String,
        relayDescription: String,
        adminPubkey: String,
        domain: String?,
    ): String {
        val serverName = domain ?: "$D(curl -s http://169.254.169.254/v1/meta-data/public-ipv4 2>/dev/null || hostname -I | awk '{print ${D}1}')"

        return buildString {
            appendLine("#!/bin/bash")
            appendLine("set -euo pipefail")
            appendLine()
            appendLine("export DEBIAN_FRONTEND=noninteractive")
            appendLine()
            appendLine("# Log everything for debugging")
            appendLine("exec > >(tee /var/log/strfry-deploy.log) 2>&1")
            appendLine("echo \"Starting strfry deployment at $D(date)\"")
            appendLine()
            appendLine("# Update system")
            appendLine("apt-get update -y")
            appendLine("apt-get upgrade -y")
            appendLine()
            appendLine("# Install dependencies")
            appendLine("apt-get install -y git build-essential libyaml-cpp-dev \\")
            appendLine("  libsecp256k1-dev libzstd-dev liblmdb-dev libflatbuffers-dev \\")
            appendLine("  libssl-dev zlib1g-dev cmake pkg-config nginx certbot \\")
            appendLine("  python3-certbot-nginx ufw")
            appendLine()
            appendLine("# Configure firewall")
            appendLine("ufw default deny incoming")
            appendLine("ufw default allow outgoing")
            appendLine("ufw allow ssh")
            appendLine("ufw allow 80/tcp")
            appendLine("ufw allow 443/tcp")
            appendLine("ufw --force enable")
            appendLine()
            appendLine("# Build strfry from source")
            appendLine("cd /opt")
            appendLine("git clone https://github.com/hoytech/strfry.git")
            appendLine("cd strfry")
            appendLine("git submodule update --init")
            appendLine("make setup-golpe")
            appendLine("make -j$D(nproc)")
            appendLine("cp strfry /usr/local/bin/")
            appendLine()
            appendLine("# Create strfry user and directories")
            appendLine("useradd -r -s /bin/false strfry || true")
            appendLine("mkdir -p /var/lib/strfry /etc/strfry")
            appendLine("chown strfry:strfry /var/lib/strfry")
            appendLine()
            appendLine("# Generate strfry config")
            appendLine("cat > /etc/strfry/strfry.conf << 'STRFRY_CONF'")
            appendLine("db = \"/var/lib/strfry/\"")
            appendLine()
            appendLine("dbParams {")
            appendLine("    maxreaders = 256")
            appendLine("    mapsize = 10995116277760")
            appendLine("}")
            appendLine()
            appendLine("events {")
            appendLine("    maxEventSize = 65536")
            appendLine("    rejectEventsNewerThanSeconds = 900")
            appendLine("    rejectEventsOlderThanSeconds = 94608000")
            appendLine("    rejectEphemeralEventsOlderThanSeconds = 60")
            appendLine("    ephemeralEventsLifetimeSeconds = 300")
            appendLine("    maxNumTags = 2000")
            appendLine("    maxTagValSize = 1024")
            appendLine("}")
            appendLine()
            appendLine("relay {")
            appendLine("    bind = \"127.0.0.1\"")
            appendLine("    port = 7777")
            appendLine()
            appendLine("    info {")
            append("        name = \"")
            append(relayName.replace("\"", "\\\""))
            appendLine("\"")
            append("        description = \"")
            append(relayDescription.replace("\"", "\\\""))
            appendLine("\"")
            append("        pubkey = \"")
            append(adminPubkey)
            appendLine("\"")
            appendLine("        contact = \"\"")
            appendLine("    }")
            appendLine()
            appendLine("    maxWebsocketPayloadSize = 131072")
            appendLine("    autoPingSeconds = 55")
            appendLine("    enableTcpKeepalive = false")
            appendLine("    queryTimesliceBudgetMicroseconds = 10000")
            appendLine("    maxFilterLimit = 500")
            appendLine("    maxSubsPerConnection = 20")
            appendLine("}")
            appendLine("STRFRY_CONF")
            appendLine()
            appendLine("chown -R strfry:strfry /etc/strfry")
            appendLine()
            appendLine("# Create systemd service")
            appendLine("cat > /etc/systemd/system/strfry.service << 'SYSTEMD_CONF'")
            appendLine("[Unit]")
            appendLine("Description=strfry Nostr relay")
            appendLine("After=network.target")
            appendLine()
            appendLine("[Service]")
            appendLine("Type=simple")
            appendLine("User=strfry")
            appendLine("ExecStart=/usr/local/bin/strfry --config /etc/strfry/strfry.conf relay")
            appendLine("Restart=on-failure")
            appendLine("RestartSec=5")
            appendLine("LimitNOFILE=65536")
            appendLine()
            appendLine("[Install]")
            appendLine("WantedBy=multi-user.target")
            appendLine("SYSTEMD_CONF")
            appendLine()
            appendLine("systemctl daemon-reload")
            appendLine("systemctl enable strfry")
            appendLine("systemctl start strfry")
            appendLine()
            appendLine("# Configure nginx as reverse proxy")
            appendLine("SERVER_IP=\"${serverName}\"")
            appendLine()
            appendLine("cat > /etc/nginx/sites-available/strfry << NGINX_CONF")
            appendLine("server {")
            appendLine("    listen 80;")
            appendLine("    server_name ${D}SERVER_IP;")
            appendLine()
            appendLine("    location / {")
            appendLine("        proxy_pass http://127.0.0.1:7777;")
            appendLine("        proxy_http_version 1.1;")
            appendLine("        proxy_set_header Upgrade ${D}http_upgrade;")
            appendLine("        proxy_set_header Connection \"upgrade\";")
            appendLine("        proxy_set_header Host ${D}host;")
            appendLine("        proxy_set_header X-Real-IP ${D}remote_addr;")
            appendLine("        proxy_set_header X-Forwarded-For ${D}proxy_add_x_forwarded_for;")
            appendLine("        proxy_set_header X-Forwarded-Proto ${D}scheme;")
            appendLine("        proxy_read_timeout 86400s;")
            appendLine("        proxy_send_timeout 86400s;")
            appendLine("    }")
            appendLine("}")
            appendLine("NGINX_CONF")
            appendLine()
            appendLine("rm -f /etc/nginx/sites-enabled/default")
            appendLine("ln -sf /etc/nginx/sites-available/strfry /etc/nginx/sites-enabled/")
            appendLine("nginx -t && systemctl reload nginx")
            appendLine()
            if (domain != null) {
                appendLine("# Set up SSL with Let's Encrypt")
                appendLine("certbot --nginx -d $domain --non-interactive --agree-tos --register-unsafely-without-email")
                appendLine()
            }
            appendLine("echo \"strfry deployment completed at $D(date)\"")
            appendLine("echo \"Relay is running on port 7777, proxied through nginx on port 80\"")
            if (domain != null) {
                appendLine("echo \"Relay URL: wss://$domain\"")
            } else {
                appendLine("echo \"Relay URL: ws://${D}SERVER_IP\"")
            }
        }
    }
}
