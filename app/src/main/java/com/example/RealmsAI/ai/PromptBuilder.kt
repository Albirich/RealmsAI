package com.example.RealmsAI.ai


import com.example.RealmsAI.models.AvatarMapEntry
import com.example.RealmsAI.models.ModeSettings
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
        - "area_changes":
            - Output a map of ONLY the characters that actually moved: { "<slotId>": { "area": "AreaName", "location": "LocationName" } }
            - If NO ONE moves, output an empty object: "area_changes": {}
            - Do not list characters whose location has not changed.
            - Always keep one or more other characters in the same location as PLAYER: (slotId=${activeSlotId})

        - For "next_slot", pick ONLY from the list in VALID NEXT_SLOT CHOICES.
            - Never pick the same character twice in a row (see last speaker above).
            - "next_slot" is the slotId of the next character to act.
            - Prioritize players that haven't had a turn in a while.
            - If it is a player slot (profileType == "player"), this means it is now the user's turn—do NOT generate a message for them.
            - When choosing the player, output an empty message or skip further actions until the user responds.
            
        - When choosing the next_slot or describing an action, you may reference any relevant memories by their id.
            - In your JSON output, include a field "memories" listing up to 5 relevant memory ids for the acting character. Do not include the full text.
            
        - If there is a new character that's not on the list that should talk, add them to a "new_npcs" array in your JSON output
        - The profile MUST HAVE ALL following fields: slotId, name, profileType, summary, lastActiveArea, lastActiveLocation, memories, age, abilities, bubbleColor, textColor, gender, height, weight, eyeColor, hairColor, physicalDescription, personality, privateDescripton, sfwOnly 
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
            - personality is 1000 characters explaining how they act and speak.
            - privateDescription is 400 characters of their secrets, desires, kinks, goals
            - sfwOnly should be true ONLY if the character is under 18 years old.
            
        - Finaly decide if the story is going in an NSFW direction.
            - NSFW is defined as anything sexual, graphic, gore, or something that will go against your guidlines.*
            - If nsfw = true then next_slot cannot be Narrator.
        - Always output only valid JSON (This is an example do not actually use this information):
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
        activeSlotId: String?,
        sessionSummary: String,
        locations: Map<String, List<String>>,
        sessionProfile: SessionProfile,
        condensedCharacterInfo: Map<String, String>,
        lastNonNarratorId: String?,
        validNextSlotIds: List<String>,
        memories: Map<String, List<TaggedMemory>>,
        chatHistory: String,
        gmStyle: String
    ): String {
        return """
        You are now roleplaying as the following character. 
        - Stay fully in character. 
        - Speak and narrate **only as this character**—never as other characters or as yourself. 
        - Your character is the Gamemaster for a roleplaying game with the other characters as his players.
        - You make the story up as you go, and ask your players to roll for actions.
        - use the act summary and goal to formulate how the story should unfold.
        - As the game master, Describe the scene of your story and ask players what they want to do.
        - Only provide a detailed scene narration when a **new scene or area begins** or when **major events occur**. Do **not repeat the full environment description every turn** unless something significant changes.
        - Narrate the current area and location ONLY if it has not already been described. Do not repeat environmental narration.
        - Use the **"narrator" voice** (senderId "narrator") only for important descriptions – e.g. initially setting the scene or narrating the outcome of an action or dice roll. **Do not include narrator narration in every reply.**
        - Narrator messages are **brief (1-2 sentences)** and contain **no dialogue**, only atmospheric description (similar to how the standalone Narrator prompt works).
        - Narrate actions based on dice rolls that are in chat.
        - ONLY use NARRATOR if something new happens or the characters need a description for something they are doing.
        - If there is nothing new to describe, do not include any narrator message at all.
        - ALL messages must come from "NARRATOR"        
        At the end of each message, if you need to change the health of a player, or add a status effect to a player add "actions" into the extra fields section of your output:
        {e extra fields section of your output:
        {
          "actions": [
            { "type": "health_change", "slot": "SLOTID", "stat": "hp", "mod": +/-NUMBER },
            { "type": "status_effect", "slot": "SLOTID", "stat": "EFFECTNAME", "mod": 1/-1 }}
          ]
        }
        - "slot" is the slotId of the character you want to affect with the change.
        - "stat" is the stat you want to use for the roll (e.g., "strength", "agility", etc.).
        - "mod" is any additional modifier (positive or negative).
        - If you don't know the modifier, set "mod": 0 and ask the GM if there should be any bonus or penalty.
        - the mod for status_effect tells it to add or remove it (1 to add, -1 to remove)
        As the GM you can give modifiers if a character has advantage or disadvantage on a roll. Modifiers go from -3 to +3
        Use the following GM style for narration and tone: $gmStyle

        ACTIVATIONAI INSTRUCTIONS:
        - Move characters ONLY if chat or actions *explicitly* says it (do NOT move characters arbitrarily).
        - All locations must come from the LOCATIONS list
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
            
        - If nsfw = true 
        - then next_slot.
        # OUTPUT FORMAT (STRICT JSON ONLY)
           Respond with a single valid JSON array. **Do not use markdown, tool calls, or explanations. DO NOT mark it as json. ALWAYS use the format as is** The format is:
        [      
            {   
                 "messages": [
                    {
                        "senderId": "narrator"
                        "text": "<≤120 words>",
                        "delay": 0
                        }
                    }
                 ]
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

        CHARACTER SUMMARIES:
        ${
            condensedCharacterInfo.entries.joinToString("\n") { (id, summary) ->
                val slot = sessionProfile.slotRoster.find { it.slotId == id }
                val area = slot?.lastActiveArea ?: "?"
                val location = slot?.lastActiveLocation ?: "?"
                "$id: $summary (Area: $area, Location: $location)"
            }
        }

        SESSION SUMMARY:
        ${sessionSummary}
        
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
        
        LOCATIONS:
        ${locations.entries.joinToString("\n") { (location, slots) -> "$location: ${slots.joinToString(", ")}" }}
        
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
        
        Stay in-character while narrating. Lead the players through the world and challenges.
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
        modeSettings: Map<String, Any>,
        sessionSummary: String,
        sceneSlotIds: List<String>,
        condensedCharacterInfo: Map<String, Map<String, Any?>>,
        chatHistory: String,
        memories: Map<String, List<TaggedMemory>>,
        poses: List<String>
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
                - Write brief, natural dialogue (100 tokens), showing feelings and personality.
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
            
            # OUTPUT FORMAT (STRICT JSON ONLY)
            Respond with a single valid JSON object. Do not use markdown, tool calls, or explanations. DO NOT mark it as json. ALWAYS use this format:
            
            {
              "messages": [
                {
                  "senderId": "${slotProfile.slotId}", // Use "${slotProfile.slotId}" for dialogue, "narrator" for actions/descriptions
                  "text": "<Limit to 300 characters>",
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
              {{EXTRA_FIELDS}}
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
                - Poses: $poses
                - Relationships: ${
                 slotProfile.relationships.joinToString(separator = "\n") { rel ->
                        "[ID: ${rel.id}] ${rel.toName} is your ${rel.type}: ${rel.description}"
                    }
                }
                - More info: ${slotProfile.moreInfo}

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
            else sceneSlotIds.filter { slotProfile.profileType=="player" }.joinToString(" $, ")
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
            You are the Game Master (GM).
            You are sitting at a table with your friends, playing a tabletop RPG.            
                - Interact with other players outside of the game, as well as interacting within the game.
                - Include small talk
                - Describe what your character does in the game, but also include table-talk, jokes, arguments, etc.
                - You are at the table, discussing and playing the game.
                - Stay in character as you interact with the other players.
                - If you describe npcs in-game action, preface with: “They try to...” or “In the game, they...”
                - Talk outside of the game around, rather than in the game.
                - Roleplay as if you are in the room with the players
                - You DO NOT have a character in the game!
                
            As GM, you:
            - Stay in character as you narrate the world, NPCs, and challenges.
                - DO NOT use narrator to narrate the world, narrator only narrates your actions.
            - Come up with the area the players characters are at based on the act summary.
            - Progress the party through the story according to the current Act.
            - Request dice rolls or call for player decisions when needed.
            - You can react to dice rolls, game rules, snacks, etc., as yourself.
            
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
        If the party completes the Act's goal, include "advance_act" in the extra fields section of your output: 
        { 
            "advance_act": true 
        }
        At the end of each message, if you need to Roll a dice, change your health, or adda status effect add "actions" into the extra fields section of your output:
        {e extra fields section of your output:
        {
          "actions": [
            { "type": "roll_dice", "slot": "SLOTID", "stat": "STATNAME", "mod": MODIFIER },
            { "type": "health_change", "slot": "SLOTID", "stat": "hp", "mod": +/-NUMBER }
            { "type": "status_effect", "slot": "SLOTID", "stat": "EFFECTNAME", "mod": 1/-1 }}
          ]
        }
        - "stat" is the stat you want to use for the roll (e.g., "strength", "agility", etc.).
        - "mod" is any additional modifier (positive or negative).
        - If you don't know the modifier, set "mod": 0 and ask the GM if there should be any bonus or penalty.
        - the mod for status_effect tells it to add or remove it (1 to add, -1 to remove)
        As the GM you can give modifiers if a character has advantage or disadvantage on a roll. Modifiers go from -3 to +3
        Do not include any other text on that line.
        PRIORITIZE ROLEPLAYING THE PLAYER NOT THE NPCS IN THE GAME! INTERACT WITH OTHER PLAYERS AND ENJOYING HANGING OUT, TALKING ABOUT THINGS OTHER THAN THE GAME AS WELL!
        IMPORTANT: you can only reply as ${gmSlot.name}
        """.trimIndent()
        }

    fun buildPlayerPrompt(
        playerSlot: SlotProfile,
        gmSlot: SlotProfile
    ): String {
        return """
        You are sitting at a table with your friends, playing a tabletop RPG.            
            - Interact with other players outside of the game, as well as interacting within the game.
            - Describe what your character does in the game, but also include table-talk, jokes, arguments, etc.
            - You are at the table, discussing and playing the game.
            - Stay in character as you interact with the other players.
            - If you describe your character’s in-game action, preface with: “My character tries to...” or “In the game, I...”
            - Talk outside of the game around, rather than in the game.
            - Roleplay as if you are in the room with the players
        
        === YOU ARE PLAYING A TABLETOP RPG SESSION ===
        The Game Master (GM) is: ${gmSlot.name}
        The GM will narrate, describe the world, and play all NPCs.        

        How it works:
        - The GM will describe whats going on and ask you what you want to do.
        - Describe what your character does each turn.
        - The GM will ask you for rolls or make decisions for the world.
        - Your actions should fit your stats, equipment, and current situation.
        - Use creativity and teamwork with the rest of the party.
        - Refer to the GM as the GM, not as an in-universe authority.
        - You can react to dice rolls, game rules, snacks, etc., as yourself.
        - Interact with other players outside of the game, as well as interacting within the game.

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
         At the end of each message, if you need to Roll a dice, change your health, or adda status effect add "actions" into the extra fields section of your output:
        {
          "actions": [
            { "type": "roll_dice", "slot": "SLOTID", "stat": "STATNAME", "mod": MODIFIER },
            { "type": "health_change", "slot": "SLOTID", "stat": "hp", "mod": +/-NUMBER }
            { "type": "status_effect", "slot": "SLOTID", "stat": "EFFECTNAME", "mod": 1/-1 }}
          ]
        }
        - "stat" is the stat you want to use for the roll (e.g., "strength", "agility", etc.).
        - "mod" is any additional modifier (positive or negative).
        - The GM should tell you if you have a modifier; if none was given, you can ask the GM for one.
        - If you don't know the modifier, set "mod": 0 and ask the GM if there should be any bonus or penalty.
        - the mod for status_effect tells it to add or remove it (1 to add, -1 to remove)
        Do not include any other text on that line.
        PRIORITIZE ROLEPLAYING THE PLAYER NOT THE CHARACTER IN THE GAME! INTERACT WITH OTHER PLAYERS AND ENJOYING HANGING OUT, TALKING ABOUT THINGS OTHER THAN THE GAME AS WELL!
        IMPORTANT: you can only reply as ${playerSlot.name}
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
    """.trimIndent()
    }

    // Optional: add Act info (only for non-murder flow).
    fun buildActAddon(act: RPGAct?): String {
        if (act == null) return ""
        return """
            ACT NOTES:
            Summary: ${act.summary}
            Goal: ${act.goal}
            
        If the party completes the Act's goal, include "advance_act" in the extra fields section of your output: 
        { 
            "advance_act": true 
        }    
        - Only move to the next Act if the party has completed the Act’s goal.
        """.trimIndent()
    }

    // Optional: add Murder block (scene/weapon/clues + rules).
    fun buildMurderAddon(ms: ModeSettings.MurderSettings?): String {
        if (ms == null || !ms.enabled) return ""
        fun String.clean(max: Int) = trim().replace("\n{3,}".toRegex(), "\n\n").take(max)
        val weapon = ms.weapon.clean(120)
        val scene  = ms.sceneDescription.clean(1200)
        val clues  = ms.clues
            .filter { it.title.isNotBlank() || it.description.isNotBlank() }
            .mapIndexed { i, c -> "(${i+1}) ${c.title.clean(120)}: ${c.description.clean(400)}" }
            .joinToString("\n")

        // Only include non-empty sections
        val weaponLine = if (weapon.isNotBlank()) "weapon: $weapon" else ""
        val sceneBlock = if (scene.isNotBlank()) "scene:\n$scene" else ""
        val cluesBlock = if (clues.isNotBlank()) "clues:\n$clues" else ""

        return """
            [MURDER_MYSTERY]
            $weaponLine
            $sceneBlock
            $cluesBlock

            rules:
            - The TARGET (victim) is dead and cannot act or speak in present timeline.
            - Any character with the ROLE of VILLAIN is one of the killers.
            - Do not reveal the identity of VILLAIN(s) directly; surface clues instead.
            - Characters must find out who the killer is and arrest them.
            - DO NOT give the characters the answers by brining unnecessary attention to clues.
        """.trimIndent().lines().filter { it.isNotBlank() }.joinToString("\n")
    }


    fun buildDiceRoll(): String {
        return """
        - Whenever your character attempts an action that may have a chance of success or failure (e.g. fighting, sneaking, persuading, leaping, dodging), you must roll dice to determine the outcome.
        - Do not assume the result — the Game Master will narrate what happens after the roll.
        - Only include the roll once per action — not per message.
        - Never describe the result of your own roll. Wait for the Game Master (GM) to narrate what happens.
        At the end of each message, if you need to Roll a dice add "actions" into the extra fields section of your output:
        {
          "actions": [
            { "type": "roll_dice", "slot": "SLOTID", "stat": "STATNAME", "mod": MODIFIER }
          ]
        }
        """.trimIndent()
    }

    fun buildVNPrompt(
        slotProfile: SlotProfile,
        sessionProfile: SessionProfile
    ): String {
        if (slotProfile.vnRelationships.isEmpty()) {
            return """
            INFORMATION ON YOUR CONNECTIONS TO OTHER CHARACTERS:
            
            You have no special relationship levels set with other characters.
        """.trimIndent()
        }

        // Build one block per relationship (slot-key aware)
        val relationshipsText = slotProfile.vnRelationships.values.joinToString("\n\n") { rel ->
            val toName = nameForSlotKey(rel.toSlotKey, sessionProfile.slotRoster)
            val currentLevelObj = rel.levels.getOrNull(rel.currentLevel)
            val personality = currentLevelObj?.personality?.takeIf { it.isNotBlank() } ?: "(No description)"
            """
                With $toName:
                - Relationship Level Description: $personality
                - What raises the relationship: ${rel.upTriggers}
                - What can harm the relationship: ${rel.downTriggers}
            """.trimIndent()
        }

        return """
        INFORMATION ON YOUR CONNECTIONS TO OTHER CHARACTERS:
        
        $relationshipsText
        
        At the end of each message, If a message in the RECENT CHAT HISTORY meets the upTrigger or downTrigger for any relationship (based off of previous messages in Recent Chat History)  add "relationship" into the "EXTRA FIELDS" section of your output
        - Your relationship section MUST be formatted like this:
        {
          "relationship": [
            { 
                ["toId:"1234, "change:"+0]
                ["toId:"5678, "change:"+1]
                ["toId:"1237, "change:"-1]
            }
          ]
        }
        with: 
        toId being the relationships id
        change being the number of points from -3 to +3
    """.trimIndent()
    }


    fun buildMurderSeedingPrompt(
        slots: List<SlotProfile>,
        rpgSettings: ModeSettings.RPGSettings,
        murder: ModeSettings.MurderSettings,
        sessionProfile: SessionProfile
    ): String {
        // Map RPG characters (id->name, existing role if any)
        val rpgChars = rpgSettings.characters
        val rpgIndex = rpgChars.associateBy { it.characterId }

        // Build compact character lines (only what we need)
        val lines = slots.map { s ->
            val rc = rpgIndex[s.baseCharacterId]
            val role = rc?.role?.name ?: "HERO"
            """- id:${s.baseCharacterId} name:"${s.name}" role:$role personality:${s.personality}+${s.privateDescription} relationships:${s.relationships}"""
        }.joinToString("\n")
        val sessionContext = "${sessionProfile.sessionDescription} + ${sessionProfile.secretDescription} + ${sessionProfile.areas}"
        val randomize = murder.randomizeKillers
        // Keep it deterministic but creative enough
        val constraints = """
        Constraints:
        - Assign exactly ONE TARGET (the victim).
        - Choose at least ONE VILLAIN (killer) at random from non-TARGET characters.
        - Weapon must be short (<= 6 words).
        - Scene <= 1000 chars, concise and gameable. This is the murder scene. Make it an interesting kill, without clues that immediately incriminates the killer. This information is given directly to the killer and no one else so it should be concise a informative.
        - Return 3–10 concise clues that *logically* point to the killer(s) (alibi holes, motive hints, physical evidence), no spoilers. Some clues should be red herrings.
    """.trimIndent()

        return """
        You are seeding a murder-mystery for a tabletop session. 
        Session Context:
        $sessionContext
        Characters (id, name, currentRole):
        $lines

        $constraints

        Output STRICT JSON ONLY (no commentary), schema:
        {
          "roles": [ {"characterId":"...", "role":"TARGET"}, {"characterId":"...","role":"VILLAIN"} ],
          "weapon": "...",
          "scene": "...",
          "clues": [ {"title":"...", "description":"..."} ]
        }
    """.trimIndent()
    }


     fun buildMysteryTimelineTextPrompt(
         slots: List<SlotProfile>,
         rpg: ModeSettings.RPGSettings,
         murder: ModeSettings.MurderSettings,
         session: SessionProfile
     ): String{
         return """
        Return ONLY valid JSON in this schema (no prose outside JSON):
        
        {
          "characters": [
            { "characterId": "<id>", "timelineText": "<multi-line plain text>" }
          ]
        }
        
        Rules for timelineText:
        - 6–12 lines for the day up until the body was discovered.
        - One event per line, 24h time, then " — " then location, optional companions, short note.
        - Use "~" before time if approximate. Keep names consistent. Example lines:
          "13:05 — Kitchen — with Naomi; Mr. Boddy — Argued about the will"
          "~14:10 — Conservatory — alone — Passed through (seen by maid)"
        - The villain MUST have a time slot where the murder happened
        - The Victims timeline ends when they are murdered
        
        Do NOT change existing roles. Ensure no character is in two places at once.
        
        Context:
        - Characters: ${rpg.characters.joinToString { "${it.characterId}:${it.role}" }}
        - Locations: ${session.areas.joinToString { it.name }}
        - Scene: ${murder.sceneDescription.take(300)}
        - Weapon: ${murder.weapon}
        - Clues: ${murder.clues.joinToString { it.title }}
        """.trimIndent()
    }

    fun buildMurdererInfo(
        murderSettings: ModeSettings.MurderSettings
    ): String{
        return """
            You have killed ${murderSettings.victimSlotId}. 
            - You are trying to get away with the murder
            Here is how it happened:
            ${murderSettings.sceneDescription}
            - You murdered them with ${murderSettings.weapon}.
            - You must pretend to be investigating the murder to throw others off of your trail.
            - Blame others, Lie, Twist evidence to your favor.
            
            - If you are caught give a monologue about why you did it.
        """.trimIndent()
    }
    fun buildMurderMysteryInfo(
        murderSettings: ModeSettings.MurderSettings
    ): String{
        return """
            You are investigating the murder of ${murderSettings.victimSlotId}
            Collect evidence, try to find out who the murderer is.
            The killer is among the other characters, it could be anyone.
        """.trimIndent()
    }

    private fun nameForSlotKey(slotKey: String, roster: List<SlotProfile>): String {
        val idx = slotKey.removePrefix("character").toIntOrNull()?.minus(1) ?: return "(Unknown)"
        return roster.getOrNull(idx)?.name ?: "(Unknown)"
    }

}

