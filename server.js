require('dotenv').config();
const http = require('http');
const next = require('next');
const { initSocket } = require('./socket');

const dev = process.env.NODE_ENV !== 'production';
const port = parseInt(process.env.PORT, 10) || 3000;
const hostname = '0.0.0.0';

const app = next({ dev, hostname, port });
const handle = app.getRequestHandler();

app.prepare().then(() => {
  const server = http.createServer((req, res) => {
    // Next.js handles all standard web & API requests
    handle(req, res);
  });

  // Attach Socket.IO to the exact same HTTP server instance
  initSocket(server);

  server.listen(port, hostname, (err) => {
    if (err) {
      console.error('Failed to start server:', err);
      process.exit(1);
    }
    console.log(`> Server ready and listening on http://localhost:${port}`);
    console.log(`> Environment: ${dev ? 'development' : 'production'}`);
  });
}).catch((err) => {
  console.error('Error preparing Next.js application:', err);
  process.exit(1);
});
