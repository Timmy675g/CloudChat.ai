// Last updated: v1.1.3 (Backend and model revamp)

export default {
  async fetch(request, env) {
    if (request.method !== "POST") {
      return Response.json(
        { error: "Method not allowed" },
        { status: 405 }
      );
    }

    const authHeader = request.headers.get("Authorization");
    const expectedSecret = `Bearer ${env.API_SECRET}`;

    if (authHeader !== expectedSecret) {
      return Response.json(
        { error: "Unauthorized" },
        { status: 401 }
      );
    }

    let data;

    try {
      data = await request.json();
    } catch {
      return Response.json(
        { error: "Invalid JSON body" },
        { status: 400 }
      );
    }

    const {
      player,
      message,
      system_prompt,
      model,

      safe_mode,
      online_players,
      max_players,
      dimension,
      world_time,

      can_access_online_player_count,
      can_access_dimension,
      can_access_world_time,
      can_access_player_coordinates
    } = data;

    const defaultModel = "@cf/meta/llama-3.1-8b-instruct";
    const allowedModels = new Set([
      defaultModel
    ]);

    if (
      model !== undefined &&
      (typeof model !== "string" || !allowedModels.has(model))
    ) {
      return Response.json(
        { error: "Unsupported model" },
        { status: 400 }
      );
    }

    const selectedModel = model || defaultModel;

    let serverContext = "";

    if (can_access_online_player_count) {
      serverContext +=
        `Online players: ${online_players}/${max_players}\n`;
    }

    if (can_access_dimension) {
      serverContext +=
        `Player dimension: ${dimension}\n`;
    }

    if (can_access_world_time) {
      serverContext +=
        `World time: ${world_time}\n`;
    }

    serverContext += `
Capabilities:
- Can access online player count: ${can_access_online_player_count}
- Can access player coordinates: ${can_access_player_coordinates}
- Can access dimension: ${can_access_dimension}
- Can access world time: ${can_access_world_time}
- Safe mode: ${safe_mode}

Important rules:
- Do not invent player counts, coordinates, structures, inventories, commands, or admin data.
- Only use server data provided in this request.
- If the requested data is not provided, say you do not have access to it.
`;

    try {
      const aiResponse = await env.AI.run(selectedModel, {
        messages: [
          {
            role: "system",
            content: `${system_prompt}\n\n${serverContext}`
          },
          {
            role: "user",
            content: `${player}: ${message}`
          }
        ]
      });

      return Response.json({
        reply: aiResponse.response || "No response from AI."
      });
    } catch (error) {
      return Response.json(
        {
          error: "Failed to get response from AI service",
          details: error instanceof Error ? error.message : "Unknown AI error"
        },
        { status: 502 }
      );
    }
  }
};