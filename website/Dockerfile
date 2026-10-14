# The website as a small static server (Caddy). On Railway: deploy this repository with the root directory
# set to website/; Railway builds this file and routes its domain to the port it gives in $PORT.
FROM caddy:2.8.4-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY . /srv
RUN rm -f /srv/Caddyfile /srv/Dockerfile /srv/.dockerignore /srv/railway.json /srv/README.md /srv/CNAME
