# Best balance of compatibility and size for M1/M2 + Railway
FROM node:18-bookworm-slim

# Set working directory
WORKDIR /app

# 1. Copy package files first for optimal caching
COPY package.json package-lock.json* ./

# 2. Install dependencies (clean cache afterwards)
RUN npm install && npm cache clean --force

# 3. Copy all other files
COPY . .

# 4. Build step (if using React/Vite)
RUN npm run build

# 5. Runtime configuration
ENV NODE_ENV=production
ENV PORT=5173
EXPOSE 5173

# 6. Start command (adjust if using different dev server)
CMD ["npm", "run", "dev"]