# CloudChat

CloudChat is an open-source AI assistant mod for Minecraft servers built for the Fabric mod loader.

It allows players to communicate with an AI directly in-game using customizable commands powered by Cloudflare Workers AI or other AI backends.

---

# Features

- AI chat directly inside Minecraft
- Configurable AI name
- Configurable command name
- Cloudflare Workers AI integration
- Async HTTP requests
- Per-server personalities
- Auto-generated configuration files
- Lightweight and simple setup
- Open-source

---

# Requirements

- Minecraft Fabric Server
- Fabric API
- Java 25
- Cloudflare Worker AI backend (or compatible API)

---

# Installation

1. Install Fabric Server
2. Install Fabric API
3. Place the CloudChat `.jar` inside:

```txt
mods/
```

4. Start the server once

5. A config file will automatically generate at:

```txt
config/cloudchat/cloudchat.properties
```

6. Edit the configuration

7. Restart the server

---

# Configuration

Example configuration:

```properties
ai_name=CloudChat
command_name=cloudchat

worker_url=https://your-worker.workers.dev
api_secret=replace_me 
Note : api_secret is not your API! It's your API Password! 

system_prompt=You are a friendly Minecraft server assistant.

cooldown_seconds=10
max_message_length=300
```

---

# Usage

Example command:

```txt
/cloudchat Hello!
```

Example response:

```txt
[CloudChat] Hello! How can I help?
```

---

# Project Goals

CloudChat aims to provide:

- A customizable AI assistant for Minecraft communities
- Lightweight AI integration for servers
- Easy API backend swapping
- Future multi-loader support
- Future dashboard management support

---

# Roadmap

## Planned Features

- NeoForge support
- Streaming AI responses
- Discord integration
- Dashboard configuration
- Multi-model support
- Local LLM support
- Memory system
- Moderation tools
- Permission integrations
- Conversation history

---

# Development Notes

CloudChat was developed with AI-assisted tooling for parts of the implementation, debugging, refactoring, and boilerplate generation.

The overall architecture, infrastructure design, integration logic, and project direction are maintained by the contributors.

---

# Contributing

Contributions, pull requests, bug reports, and suggestions are welcome.

If you want to contribute:

1. Fork the repository
2. Create a new branch
3. Make your changes
4. Open a pull request

---

# License

MIT License

Feel free to use, modify, and distribute this project under the terms of the MIT License.