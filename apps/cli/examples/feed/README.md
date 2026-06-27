# Example: a sample "Now" feed

Two ready-to-push briefing cards: an **invite** action card and a **contact** card
that **deep-links** into the example hub (`../hub-college`). Use it to see the Now
feed render — and the signature card→hub deep-link in action.

## Push it
1. `dayfold login`
2. Push the deep-link target first: `bash ../hub-college/push.sh`
3. `bash push.sh`
4. Open the app → **Now**. Tap **"Financial Aid Office"** → it opens the college
   hub's *Money & Forms* section.

Cards are kept valid by `ExampleCardsValidateTest` (CI). Note: `dayfold template
<type>` already prints realistic single-card starters — this set adds a multi-card
feed plus the deep-link demo. Field reference: `../README.md`.
