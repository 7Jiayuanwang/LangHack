# Stage 1: Build the Vue application
FROM node:20-alpine AS builder

# Accept build arguments
ARG VITE_API_BASE_URL
ARG VITE_TUTOR_API_BASE_URL

# Set environment variables for the build process
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_TUTOR_API_BASE_URL=$VITE_TUTOR_API_BASE_URL

WORKDIR /app

# Copy package.json and package-lock.json
COPY package*.json ./

# Install dependencies
RUN npm install

# Copy source code
COPY . .

# Build the application
RUN npm run build

# Stage 2: Serve the application with Nginx
FROM nginx:alpine

# Copy built assets from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy maintenance page (for auto-update maintenance mode)
COPY maintenance.html /usr/share/nginx/html/maintenance.html

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80
EXPOSE 80

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
