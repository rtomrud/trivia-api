# trivia-api

A REST API for a trivia game.

## Running

```bash
docker compose up
```

## Functional requirements

- R01. A player can create a room. Each room has a unique and random URL that can be shared with other players.
- R02. A player can join a room, given the URL of that room. The player must specify a username when joining a room. The first player to join a room is the host of that room.
- R03. The host can create and delete teams. A player can join a team. The host can put any player into any team.
- R04. The host can create a game for the players in a room.
- R05. The host can configure the rounds, time per round and questions per round of a game.
- R06. When a round of a game starts, a players can see the questions of that round. A question can be either: Multiple Choice (N options to chose from), Short Answer (open-ended reply) or Buzzer (only the first correct answer scores). A question has text and may also have media (audio or video).
- R07. A player can answer a question until the round ends, and they must not be able to know whether the answer is correct until the end of the round.
- R08. After a round ends, a player can see the correct answers of each question of that round.
- R09. After a round ends, a player can see the answer of each player to each question of that round.
- R10. A player can see the score (based on the amount of correct answers) earned by each player and team.

