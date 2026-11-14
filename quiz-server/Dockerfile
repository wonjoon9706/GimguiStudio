FROM node:22-bookworm-slim
WORKDIR /app
COPY --chown=node:node package.json server.mjs ./
ENV NODE_ENV=production HOST=0.0.0.0 PORT=8787
USER node
EXPOSE 8787
CMD ["node", "server.mjs"]
