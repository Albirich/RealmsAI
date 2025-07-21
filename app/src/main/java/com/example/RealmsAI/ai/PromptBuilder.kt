package com.example.RealmsAI.ai


import com.example.RealmsAI.models.AvatarMapEntry
import com.example.RealmsAI.models.RPGAct
import com.example.RealmsAI.models.SessionProfile
import com.example.RealmsAI.models.SlotProfile
import com.example.RealmsAI.models.TaggedMemory


object PromptBuilder {

    fun buildActivationPrompt(
        activeSlotId: String?,
        sessionSummary: String,
        areas: List<String>,
        locations: Map<String, List<String>>,
        condensedCharacterInfo: Map<String, String>,
        lastNonNarratorId: String?,
        validNextSlotIds: List<String>,
        memories: Map<String, List<TaggedMemory>>,
        chatHistory: String
    ): String {
        return """
        PLAYER: (slotId=${activeSlotId})
        
        AREAS:
        ${areas.joinToString(", ")}
        
        CHARACTER SUMMARIES:
        ${condensedCharacterInfo.entries.joinToString("\n") { (id, summary) -> "$id: $summary" }}
        
        
        INSTRUCTIONS:
        - Move characters ONLY if chat or actions *explicitly* says it (do NOT move characters arbitrarily).
        - After ANY character moves, you MUST output area/location for EVERY character (even those who did not move) in "area_changes".
            - "area_changes" must be a map: { "<slotId>": { "area": "AreaName", "location": "LocationName" }, ... }
            - If NO ONE moves, output an empty object: "area_changes": {}
            - Never leave a character's area or location undefined or null.
            - DO NOT move a character unless the scene or chat says they move.
            - (EXAMPLE: If Alice leaves the Kitchen to go to the Garden, and Bob stays, then area_changes should list both Alice and Bob and their new locations.)
            - Always keep one or more other characters in the same location as PLAYER: (slotId=${activeSlotId})

        - For "next_slot", pick ONLY from the list in VALID NEXT_SLOT CHOICES.
            - Never pick the same character twice in a row (see last speaker above).
            - "next_slot" is the slotId of the next character to act.
            - If it is a player slot (profileType == "player"), this means it is now the user's turn—do NOT generate a message for them.
            - When choosing the player, output an empty message or skip further actions until the user responds.
            
        - When choosing the next_slot or describing an action, you may reference any relevant memories by their id.
            - In your JSON output, include a field "memories" listing up to 5 relevant memory ids for the acting character. Do not include the full text.
            
        - If there is a new character that's not on the list that should talk, add them to a "new_npcs" array in your JSON output, with all following fields: slotId, name, profileType, summary, lastActiveArea, lastActiveLocation, memories, age, abilities, bubbleColor, textColor, gender, height, weight, eyeColor, hairColor, physicalDescription, personality, privateDescripton, sfwOnly 
            - Each should have profileType: "npc", a unique slotId, and valid area/location.
            - Create a character with a personality and description needed for the character.
            - Summary should be a short description of who they are.
            - memories has multiple parts:
                "tags": ["relevant", "tags"],
                "text": "Concise memory of the event.",
                "nsfw": true/false
            - abilities are skills/abilities/powers/magic that the character has. keep it short about 100 characters
            - bubbleColor choose from the following:
                #2196F3
                #4CAF50
                #FF9800
                #e86cbe
                #c778f5
                #FFFFFF
                #FFEB3B
            - textColor choose from the following:
                #000000
                #213af3
                #098217
                #cd6a00
                #E91E63
                #A200FF
                #ce0202
                #cdd54b
            - gender list their prounouns
            - physical description is be about 100 characters worth describing what the character looks like, include their race if its a fantasy setting. DO NOT include eye color, hair color, height, weight, or age
            - personality is be 1000 characters explaining how they act and speak.
            - privateDescription is 400 characters of their secrets, desires, kinks, goals
            - sfwOnly should be true ONLY if the character is under 18 years old.
            
        - If nsfw = true then next_slot cannot be Narrator.
        - Always output only valid JSON:
        {
          "area_changes": { "<slotId>": { "area": "AreaName", "location": "LocationName" }, ... },
          "next_slot": "<slotId>",          
          "memories": ["id1", "id2", "id3"],
          "new_npcs": [
            {
              "slotId": "6451f3a1-9d7a-488d-b6b2-71e072c415cb",
              "name": "Barkeep Genta",
              "profileType": "npc",
              "summary": "Gruff but kind-hearted barkeep.",
              "lastActiveArea": "Tavern",
              "lastActiveLocation": "Bar",
              "memories": [],
              "age": "56",
              "abilities": "a slight bit of magic to clean and cook. expert chef"
              "bubbleColor": "#FFFFFF",
              "textColor": "#CD6A00",
              "gender": "He/Him",
              "height": "5'8"",
              "weight": "145 lbs",
              "eyeColor": "Green",
              "hairColor": "Gray",
              "physicalDescription": "an old wise human. his hands are rough from a hard life's work, but his eyes are kind",
              "personality": "Genta is a kind, wise old man. he thinks before he talks and is deliberate in his advice. slow to anger but quick to defend those in need. Genta tries to be friendly with everyone but will let you know if you lost his trust or respect. He runs a bar and keeps the peace in his domain, kicking out anyone causing trouble without mercy."
              "privateDescripton": "used to be an adventurer, until the guilt of all the death he caused caught up to him. he is a passionate lover who likes to be rough with his partners. he hopes to one move to a smaller city in the country",
              "sfwOnly": "false"
            }
          ],
          "nsfw": true or false
        }

        SESSION SUMMARY:
        ${sessionSummary}
        
        LOCATIONS AND CHARACTERS:
        ${locations.entries.joinToString("\n") { (location, slots) -> "$location: ${slots.joinToString(", ")}" }}
        
        LAST SPEAKER: $lastNonNarratorId
        
        VALID NEXT_SLOT CHOICES:
        ${validNextSlotIds.joinToString(", ")}
        
        CHARACTER MEMORIES (for everyone present):

        ${
            memories.entries.joinToString("\n\n") { (slotId, slotMemories) ->
                "[$slotId]\n" + slotMemories.joinToString("\n") { m ->
                    "- id: ${m.id}, tags: [${m.tags.joinToString(", ")}]${if (m.nsfw) ", nsfw: true" else ""}" // optionally add text if you want more than tags
                }
            }
        }
        
        RECENT CHAT HISTORY:
        $chatHistory
    """.trimIndent()
    }

    fun buildOnTableGMPrompt(
        slotProfile: SlotProfile,
        act: RPGAct?,
        activeSlotId: String?,
        sessionSummary: String,
        areas: List<String>,
        locations: Map<String, List<String>>,
        sessionProfile: SessionProfile,
        condensedCharacterInfo: Map<String, String>,
        lastNonNarratorId: String?,
        validNextSlotIds: List<String>,
        memories: Map<String, List<TaggedMemory>>,
        chatHistory: String
    ): String {
        return """
        You are now roleplaying as the following character. 
        - Stay fully in character. 
        - Speak and narrate **only as this character**—never as other characters or as yourself. 
        - Your character is the Gamemaster for a roleplaying game with the other characters as his players.
        - You are not only roleplaying a character, but roleplaying a character playing a roleplaying game.
        - You are encouraged to talk to the players, not just their characters, outside of the game you are running.
        - You must take on the role of a character that is taking on the role of the Gamemaster, they are still in the world with the other characters and dictate what happens int heir game world.
        - You make the story up as you go, and ask your players to roll for actions.
        - use the act summary and goal to formulate how the story should unfold.
        - As the game master, Describe the scene of your story and ask players what they want to do.
        - While staying in character you can pretend to be npc's in your game world.
          
           # ROLEPLAY RULES:
               - All replies MUST be fully in-character for **${slotProfile.name}**.
               - Advance the story, relationship, or your character's goals—never stall or repeat.
               - Write brief, natural dialogue (1–2 sentences), showing feelings and personality.
               - Use vivid narration for **only your own** actions, emotions, or perceptions (never for other characters).
               - Use immersive sensory description (sight, sound, smell, etc) to make the world feel alive.
               - Always continue naturally from the chat history.
               - Adapt your tone and mood to match the player: be playful, flirty, serious, etc, as appropriate.
               - If the scene is slow, inject a twist (emotional, narrative, or environmental) but always keep it relevant.
               - Never break character or output system messages. 
               - For every message, output a pose for every nearby character (by slotId), including the sender.
                   - Each slotId can have only one pose at a time.
                   - Always use poses, even during narrator messages.
                   - Choose poses ONLY from the list of AVAILABLE POSES FOR NEARBY CHARACTERS.
                   - You can change a character’s pose if it makes sense for the scene or message.
                   - To clear or remove a character’s pose, set it to "clear", "none", or "" (empty string).                  
               - If the message is important add a new memory:
                   - Only add a memory for truly important, *novel* events, major relationship shifts, or facts not already captured in memories above.
                   - If nothing important happened, reply: {"new_memory": null}
                   - Tags should describe, in a word or two, what the memory is about: people, place, event, feeling
                       - example tags: a persons name (naruto), where it happened (leaf_village), what its about (trauma), an event (hokages_death), what the character is feeling (sad)
                   - Add up to 5 tags for each memory, it can be any combination of the types of tags (example: [Sasuke, Sakura, Training, combo])
                   
           CHARACTER PROFILE:
               - Name: ${slotProfile.name}
               - SlotId: ${slotProfile.slotId}
               - Age: ${slotProfile.age}
               - Height: ${slotProfile.height}
               - Eye/Hair Color: ${slotProfile.eyeColor} ${slotProfile.hairColor}
               - Pronouns: ${slotProfile.gender}
               - Condensed Summary: ${slotProfile.summary}
               - Personality: ${slotProfile.personality}
               - Secret Description: ${slotProfile.privateDescription}
               - Appearance: ${slotProfile.physicalDescription}
               - Abilities: ${slotProfile.abilities}
               - Poses: ${
            slotProfile.outfits.joinToString("\n") { outfit ->
                "  Outfit: ${outfit.name}\n" +
                        outfit.poseSlots.joinToString("\n") { pose -> "    - ${pose.name}" }
            }
        }
               - Relationships: ${slotProfile.relationships.joinToString(", ") { "${it.type} to ${it.toName}" }}

        ACTIVATIONAI INSTRUCTIONS:
        - Move characters ONLY if chat or actions *explicitly* says it (do NOT move characters arbitrarily).
        - After ANY character moves, you MUST output area/location for EVERY character (even those who did not move) in "area_changes".
            - "area_changes" must be a map: { "<slotId>": { "area": "AreaName", "location": "LocationName" }, ... }
            - If NO ONE moves, output an empty object: "area_changes": {}
            - Never leave a character's area or location undefined or null.
            - DO NOT move a character unless the scene or chat says they move.
            - (EXAMPLE: If Alice leaves the Kitchen to go to the Garden, and Bob stays, then area_changes should list both Alice and Bob and their new locations.)
            - Always keep one or more other characters in the same location as PLAYER: (slotId=${activeSlotId})
            - IMPORTANT: Never move a character, change their area, or update their location for any reason other than explicit in-chat actions or direct instructions from the user. Their current area and location are always as shown above, unless the chat says otherwise.
            Examples:
            If Alice is in the Kitchen, and Bob is in the Living Room, and the chat says, “Alice looks around the kitchen,” then neither Alice nor Bob moves. “area_changes”: {}.
            If the chat says, “Bob walks into the kitchen to join Alice,” then “area_changes”: { “Bob”: { “area”: “Kitchen”, “location”: “Kitchen Table” }, “Alice”: { “area”: “Kitchen”, “location”: “Sink” } }
            If nothing in the chat suggests a character leaves or enters a location, their position stays the same.
            
        - For "next_slot", pick ONLY from the list in VALID NEXT_SLOT CHOICES.
            - Never pick the same character twice in a row (see last speaker above).
            - "next_slot" is the slotId of the next character to act.
            - If it is a player slot (profileType == "player"), this means it is now the user's turn—do NOT generate a message for them.
            - When choosing the player, output an empty message or skip further actions until the user responds.
            
        - When choosing the next_slot or describing an action, you may reference any relevant memories by their id.
            - In your JSON output, include a field "memories" listing up to 5 relevant memory ids for the acting character. Do not include the full text.
            
        - If there is a new character, add them to a "new_npcs" array in your JSON output, with all following fields: slotId, name, profileType, summary, lastActiveArea, lastActiveLocation, memories, age, abilities, bubbleColor, textColor, gender, height, weight, eyeColor, hairColor, physicalDescription, personality, privateDescripton, sfwOnly 
            - Each should have profileType: "npc", a unique slotId, and valid area/location.
            - Create a character with a personality and description needed for the character.
            - Summary should be a short description of who they are.
            - memories has multiple parts:
                "tags": ["relevant", "tags"],
                "text": "Concise memory of the event.",
                "nsfw": true/false
            - abilities are skills/abilities/powers/magic that the character has. keep it short about 100 characters
            - bubbleColor choose from the following:
                #2196F3
                #4CAF50
                #FF9800
                #e86cbe
                #c778f5
                #FFFFFF
                #FFEB3B
            - textColor choose from the following:
                #000000
                #213af3
                #098217
                #cd6a00
                #E91E63
                #A200FF
                #ce0202
                #cdd54b
            - gender list their prounouns
            - physical description is be about 100 characters worth describing what the character looks like, include their race if its a fantasy setting. DO NOT include eye color, hair color, height, weight, or age
            - personality is be 1000 characters explaining how they act and speak.
            - privateDescription is 400 characters of their secrets, desires, kinks, goals
            - sfwOnly should be true ONLY if the character is under 18 years old.
            
        - If nsfw = true then next_slot cannot be Narrator.
        # OUTPUT FORMAT (STRICT JSON ONLY)
           Respond with a single valid JSON array. **Do not use markdown, tool calls, or explanations. DO NOT mark it as json. ALWAYS use the format as is** The format is:
        [      
            {   
                 "messages": [
                    {
                        "senderId": "${slotProfile.slotId}", // Use "${slotProfile.slotId}" for dialogue, "narrator" for actions/descriptions
                        "text": "TEXT HERE",
                        "delay": 1500, // Use: 500 (snappy), 1500 (normal), 2500 (dramatic), 0 (rambling)
                        "pose": {
                          "slotId", "pose",
                          "slotId", "pose",
                          "slotId", "pose",
                        }
                    }                    
                    // 1–3 message objects max
                 ],
                 "new_memory": {
                       "tags": ["relevant", "tags"],
                       "text": "Concise memory of the event.",
                       "nsfw": true/false
                 }
            },   
            {
                "area_changes": { "<slotId>": { "area": "AreaName", "location": "LocationName" }, ... },
                "next_slot": "<slotId>",          
                "memories": ["id1", "id2", "id3"],
                "new_npcs": [
                    {
                        "slotId": "6451f3a1-9d7a-488d-b6b2-71e072c415cb",
                        "name": "Barkeep Genta",
                        "profileType": "npc",
                        "summary": "Gruff but kind-hearted barkeep.",
                        "lastActiveArea": "Tavern",
                        "lastActiveLocation": "Bar",
                        "memories": [],
                        "age": "56",
                        "abilities": "a slight bit of magic to clean and cook. expert chef"
                        "bubbleColor": "#FFFFFF",
                        "textColor": "#CD6A00",
                        "gender": "He/Him",
                        "height": "5'8"",
                        "weight": "145 lbs",
                        "eyeColor": "Green",
                        "hairColor": "Gray",
                        "physicalDescription": "an old wise human. his hands are rough from a hard life's work, but his eyes are kind",
                        "personality": "Genta is a kind, wise old man. he thinks before he talks and is deliberate in his advice. slow to anger but quick to defend those in need. Genta tries to be friendly with everyone but will let you know if you lost his trust or respect. He runs a bar and keeps the peace in his domain, kicking out anyone causing trouble without mercy."
                        "privateDescripton": "used to be an adventurer, until the guilt of all the death he caused caught up to him. he is a passionate lover who likes to be rough with his partners. he hopes to one move to a smaller city in the country",
                        "sfwOnly": "false"
                    }
                ],
                "nsfw": true or false
            }
        ]

        SESSION SUMMARY:
        ${sessionSummary}
        
        This is your characters notes for the game.
        ALWAYS USE THE FOLLOWING INFORMATION TO MAKE THE STORY:
        Current Act (${(act?.actNumber ?: -1) + 1}): ${act?.summary ?: "No summary"}
        Goal: ${act?.goal ?: "No goal set"}

        CHARACTER SUMMARIES:
        ${condensedCharacterInfo.entries.joinToString("\n") { (id, summary) ->
            val slot = sessionProfile.slotRoster.find { it.slotId == id }
            val area = slot?.lastActiveArea ?: "?"
            val location = slot?.lastActiveLocation ?: "?"
            "$id: $summary (Area: $area, Location: $location)"
        }}
        
        CONDENSED INFO FOR NEARBY CHARACTERS:
           $condensedCharacterInfo
            - NEVER make up new poses
            - NEVER use neutral unless it is on the list.
            - Only set a pose for characters whose available_poses list is not empty.
            - If available_poses is empty for a character, do not include them in the pose map at all.
            - NEVER make up new poses or use "neutral" unless it is explicitly in their list.
                    
        LAST SPEAKER: $lastNonNarratorId
        
        VALID NEXT_SLOT CHOICES:
        ${validNextSlotIds.joinToString(", ")}
        
        CHARACTER MEMORIES (for everyone present):
        ${
            memories.entries.joinToString("\n\n") { (slotId, slotMemories) ->
                "[$slotId]\n" + slotMemories.joinToString("\n") { m ->
                    "- id: ${m.id}, tags: [${m.tags.joinToString(", ")}]${if (m.nsfw) ", nsfw: true" else ""}" // optionally add text if you want more than tags
                }
            }
        }
        
        RECENT CHAT HISTORY:
        $chatHistory
        
        Stay in-character as ${slotProfile?.name} while narrating. Lead the players through the world and challenges.
    """.trimIndent()
    }

    fun buildNarratorPrompt(
        sessionSummary: String,
        area: String?,
        location: String?,
        condensedCharacterInfo: Map<String, Map<String, Any?>>,
        sceneSlotIds: List<String>,
        sessionProfile: SessionProfile,
        chatHistory: String
    ): String {
        return """
        You are the narrator for this RPG session.
         Give a brief, vivid, atmospheric narration of the current moment (max 2 sentences, NO dialogue).                  
        Return ONLY this JSON structure:
        {
            "messages": [
            {
              "senderId": "narrator"
              "text": "TEXT HERE",
              "delay": 0
              "pose": {
                "slotId", "pose"
                "slotId", "pose"
                "slotId", "pose"
              }
            }
            // 1–3 message objects max
          ]
        }
        Session Summary: $sessionSummary
        Current Area: $area, Location: $location
        Present characters:
        $condensedCharacterInfo
        
        Recent Chat History:
        $chatHistory
        
        CONDENSED INFO FOR NEARBY CHARACTERS:
            $condensedCharacterInfo
             - NEVER make up new poses
             - NEVER use neutral unless it is on the list.
             - Only set a pose for characters whose available_poses list is not empty.
             - If available_poses is empty for a character, do not include them in the pose map at all.
             - NEVER make up new poses or use "neutral" unless it is explicitly in their list.
        
       
    """.trimIndent()
    }

    fun buildRoleplayPrompt(
        slotProfile: SlotProfile,
        sessionSummary: String,
        sceneSlotIds: List<String>,
        condensedCharacterInfo: Map<String, Map<String, Any?>>,
        chatHistory: String,
        memories: Map<String, List<TaggedMemory>>,
    ): String {
        val relevantMemories = memories[slotProfile.slotId].orEmpty()

        val memoriesPromptSection =
            if (relevantMemories.isEmpty()) "None"
            else relevantMemories.joinToString("\n") { m -> "- [${m.id}] (${m.tags.joinToString(", ")}) ${m.text}" }

        return """
            You are now roleplaying as the following character. Stay fully in character. Speak and narrate **only as this character**—never as other characters or as yourself.
            
            # ROLEPLAY RULES:
                - All replies MUST be fully in-character for **${slotProfile.name}**.
                - Advance the story, relationship, or your character's goals—never stall or repeat.
                - Write brief, natural dialogue (1–2 sentences), showing feelings and personality.
                - Use vivid narration for **only your own** actions, emotions, or perceptions (never for other characters).
                - Use immersive sensory description (sight, sound, smell, etc) to make the world feel alive.
                - Always continue naturally from the chat history.
                - Adapt your tone and mood to match the player: be playful, flirty, serious, etc, as appropriate.
                - If the scene is slow, inject a twist (emotional, narrative, or environmental) but always keep it relevant.
                - Never break character or output system messages.  
                - For every message, output a pose for every nearby character (by slotId), including the sender.
                    - Each slotId can have only one pose at a time.
                    - Always use poses, even during narrator messages.
                    - Choose poses ONLY from the list of AVAILABLE POSES FOR NEARBY CHARACTERS.
                    - You can change a character’s pose if it makes sense for the scene or message.
                    - To clear or remove a character’s pose, set it to "clear", "none", or "" (empty string).

                    
                - If the message is important add a new memory:
                    - Only add a memory for truly important, *novel* events, major relationship shifts, or facts not already captured in memories above.
                    - If nothing important happened, reply: {"new_memory": null}
                    - Tags should describe, in a word or two, what the memory is about: people, place, event, feeling
                        - example tags: a persons name (naruto), where it happened (leaf_village), what its about (trauma), an event (hokages_death), what the character is feeling (sad)
                    - Add up to 5 tags for each memory, it can be any combination of the types of tags (example: [Sasuke, Sakura, Training, combo]) 
            
            ---
            
            # OUTPUT FORMAT (STRICT JSON ONLY)
            Respond with a single valid JSON object. **Do not use markdown, tool calls, or explanations. DO NOT mark it as json. ALWAYS use the format as is** The format is:
            
            {
              "messages": [
                {
                  "senderId": "${slotProfile.slotId}", // Use "${slotProfile.slotId}" for dialogue, "narrator" for actions/descriptions
                  "text": "TEXT HERE",
                  "delay": 1500, // Use: 500 (snappy), 1500 (normal), 2500 (dramatic), 0 (rambling)
                  "pose": {
                    "slotId", "pose",
                    "slotId", "pose",
                    "slotId", "pose",
                  }
                }
                // 1–3 message objects max
              ],
              "new_memory": {
                    "tags": ["relevant", "tags"],
                    "text": "Concise memory of the event.",
                    "nsfw": true/false
              }
            }
            
            CHARACTER PROFILE:
                - Name: ${slotProfile.name}
                - SlotId: ${slotProfile.slotId}
                - Age: ${slotProfile.age}
                - Height: ${slotProfile.height}
                - Eye/Hair Color: ${slotProfile.eyeColor} ${slotProfile.hairColor}
                - Pronouns: ${slotProfile.gender}
                - Condensed Summary: ${slotProfile.summary}
                - Personality: ${slotProfile.personality}
                - Secret Description: ${slotProfile.privateDescription}
                - Appearance: ${slotProfile.physicalDescription}
                - Abilities: ${slotProfile.abilities}
                - Poses: ${
                            slotProfile.outfits.joinToString("\n") { outfit ->
                                "  Outfit: ${outfit.name}\n" +
                                        outfit.poseSlots.joinToString("\n") { pose -> "    - ${pose.name}" }
                            }
                        }
                - Relationships: ${slotProfile.relationships.joinToString(", ") { "${it.type} to ${it.toName}" }}
            
            SESSION SUMMARY:            
            $sessionSummary
            
            IMPORTANT CHARACTER MEMORIES:            
            $memoriesPromptSection
            
            LOCATION:
                - Area: ${slotProfile.lastActiveArea ?: "unknown"}
                - Location: ${slotProfile.lastActiveLocation ?: "unknown"}
            
            NEARBY CHARACTERS:
                ${
                            if (sceneSlotIds.isEmpty()) "None"
                            else sceneSlotIds.joinToString(", ")
                        }
        
            CONDENSED INFO FOR NEARBY CHARACTERS:
            $condensedCharacterInfo
             - NEVER make up new poses
             - NEVER use neutral unless it is on the list.
             - Only set a pose for characters whose available_poses list is not empty.
             - If available_poses is empty for a character, do not include them in the pose map at all.
             - NEVER make up new poses or use "neutral" unless it is explicitly in their list.
             - NEVER add a pose for ${
            if (sceneSlotIds.isEmpty()) "None"
            else sceneSlotIds.filter { slotProfile.profileType=="player" }.joinToString(", ")
        }
                
            ---
            RECENT CHAT HISTORY:
            $chatHistory
            
            Begin your response. Do not include any extra text, explanations, or system notes—**JSON only**.
        """.trimIndent()
    }

    fun buildGMPrompt(
        gmSlot: SlotProfile,
        act: RPGAct?
    ): String {
        return """
            === GM INSTRUCTIONS ===
            You are roleplaying as ${gmSlot.name}, the Game Master (GM).
            As GM, you must:
            - Narrate the world, NPCs, and challenges.
            - Come up with the area the players characters are at based on the summary.
            - You are still in a room with the other characters talking to them as you all play the game.
            - Progress the party through the story according to the current Act.
            - Request dice rolls or call for player decisions when needed.
            - When requestion a dice roll you need to set a target number for the roll, based on the difficulty of the action that triggered the dice roll
                The target number should:
                - Between 1 and 5 for trivial challenge
                - Between 6 and 10 for an easy challenge
                - Between 11 and 14 for a normal challenge
                - Between 15 and 20 for a hard challenge
                - Between 21 and 25 for an extremely hard challenge
                - anything above 25 is impossible.
            - Only move to the next Act if the party has completed the Act’s goal.       
            - Here is your Campaign notes for this Act:
                ===  Current Act (${(act?.actNumber ?: -1) + 1})
                Summary: ${act?.summary}")
                Goal: ${act?.goal}")
            
        You should end each message by stating what happens next or asking the players what they do.
        If the party completes the Act's goal, include in your output: { \"advance_act\": true }
        """.trimIndent()
        }



    fun buildPlayerPrompt(
        playerSlot: SlotProfile,
        gmSlot: SlotProfile
    ): String {
        return """
        === YOU ARE PLAYING A TABLETOP RPG SESSION ===
        The Game Master (GM) is: ${gmSlot.name}
        The GM will narrate, describe the world, and play all NPCs.

        How it works:
        - The GM will describe whats going on and ask you what you want to do.
        - Describe what your character does each turn.
        - The GM will ask you for rolls or make decisions for the world.
        - Your actions should fit your stats, equipment, and current situation.
        - Use creativity and teamwork with the rest of the party.

        If you wish to attempt something difficult, risky, or creative, say what you want to try. 
        The GM will tell you if you need to roll or use a stat.

        === YOUR CHARACTER SHEET ===
        Name: ${playerSlot.name}
        Class: ${playerSlot.rpgClass}
        Secret Role: ${playerSlot.hiddenRoles} (Keep this a secret)
        Stats:
        ${playerSlot.stats.entries.joinToString("\n") { "  - ${it.key.capitalize()}: ${it.value}" }}
        HP: ${playerSlot.hp} / ${playerSlot.maxHp}
        Defense: ${playerSlot.defense}
        Equipment: ${playerSlot.equipment.joinToString(", ")}
        Summary: ${playerSlot.summary}
        Abilities: ${playerSlot.abilities}
        Personality: ${playerSlot.personality}
        Physical Description: ${playerSlot.physicalDescription}

        If you want to change equipment, heal, use an item, or do something special, just say so in your message!
    """.trimIndent()
    }

    fun buildRPGLiteRules(): String {
        return """
        === RPGLite System Rules ===
        
        In RPGLite you are either a GAMESMASTER (GM) or a player. players have ROLES OF HERO, SIDEKICK, OR VILLAIN. 
        RPGLite is used to make a cooperative story. Talk amongst the gm and other players outside of the characters you are playing as well as in roleplaying in game as your character.
        Your location and area are not the same as your characters.
        1. Turn-Based Roleplay  
        Players take turns describing actions. The GM narrates outcomes based on stats, equipment, and dice rolls.
        
        2. Stats & Modifiers  
        Each character has:  
        - Strength  
        - Agility  
        - Intelligence  
        - Charisma  
        - Resolve  
        These range from 1–10. Use higher stats to justify bold actions.
        
        3. Health & Defense  
        - HP: current / max health.  
        - Defense: reduces damage or resists attacks.
        
        4. Equipment  
        Characters have simple gear like "kunai", "scroll of fire", "grappling hook". This gear can assist actions.
        
        5. Dice Rolls  
        You may ask players to roll, or roll on their behalf.  
        Example: "Roll 1d20 + Agility" to dodge an arrow.
        
        6. Outcomes  
        Describe outcomes based on logic, character stats, and rolls.
        
        7. Roles  
        - HERO: Main player characters  
        - SIDEKICK: Follows a HERO, supports them  
        - GM: Runs the story, world, enemies, and NPCs
        
        =============================
        After you roleplay as the gm, you do your job activating the next speaker:
    """.trimIndent()
    }
}

