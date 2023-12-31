# ReferralHat
This is a simple plugin that rewards players for referring your server to others.

Players can type /invite <player> to invite an online player as a referral. They can accept using /accept <player>

If the invited player plays on the server for a total of "x" hours, the person who invited that player will be rewarded with access to /hat.

Using /hat will set the player's helmet slot to a diamond block (i.e. they have access to a diamond block hat). Using the command again will unequip the hat.

Checks are in place to ensure:
- A specific player can only be sent an invite every 60 seconds.
- Players cannot /invite themselves.
- If a player has already accepted an invite, they can no longer accept other invites.
- If a player has already played on the server for "x" hours without being invited, they are ineligible as an invitee.
- Players cannot equip the referral hat if they are already wearing some sort of helmet.
- Players cannot take off the referral hat manually.
- If the player dies while wearing the referral hat, it will not drop as an item.

Additionally, two yaml files are used to store who invited who, and player playtime. Thus, this data persists between server restarts.

# Dependencies
- Luckperms (plugin grants access to /hats by giving players the "custom.hat" permission node)
