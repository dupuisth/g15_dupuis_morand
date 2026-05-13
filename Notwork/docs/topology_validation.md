# Configuration de validation TOPOLOGY

Cette configuration lance cinq serveurs locaux avec un graphe en diamant et une
queue:

```text
S1
| \
S2 S3
|   |
S4--+
|
S5
```

Elle permet de valider les paquets universels `TOPOLOGY` sur plusieurs cas:

- annonce additive initiale des voisins directs;
- propagation multi-sauts jusqu'a `S5`;
- chemins alternatifs entre `S1` et `S4` via `S2` ou `S3`;
- suppression de topologie quand un serveur est arrete;
- presence d'un client sur chaque serveur pour verifier que les routes restent
  utilisables apres convergence.

## Lancement IntelliJ

Utiliser la configuration de lancement:

```text
Topology validation
```

Elle execute `com.gr15.Application` avec les arguments suivants:

```bash
admin \
  server=1:2201:2202:2203/2:127.0.0.1:2212/3:127.0.0.1:2222 \
  server=2:2211:2212:2213/1:127.0.0.1:2202/4:127.0.0.1:2232 \
  server=3:2221:2222:2223/1:127.0.0.1:2202/4:127.0.0.1:2232 \
  server=4:2231:2232:2233/2:127.0.0.1:2212/3:127.0.0.1:2222/5:127.0.0.1:2242 \
  server=5:2241:2242:2243/4:127.0.0.1:2232 \
  client=127.0.0.1:2201 \
  client=127.0.0.1:2211 \
  client=127.0.0.1:2221 \
  client=127.0.0.1:2231 \
  client=127.0.0.1:2241
```

## Ports utilises

| Serveur | Client | Serveur | Admin |
| ------- | ------ | ------- | ----- |
| S1 | 2201 | 2202 | 2203 |
| S2 | 2211 | 2212 | 2213 |
| S3 | 2221 | 2222 | 2223 |
| S4 | 2231 | 2232 | 2233 |
| S5 | 2241 | 2242 | 2243 |

Tous les ports restent dans la plage autorisee par `ServerConfig`.

## Scenario conseille

1. Lancer `Topology validation`.
2. Dans chaque console admin, choisir `LIST_CONNECTIONS` pour verifier les
   connexions serveur voisines.
3. Depuis un client de `S1`, envoyer un message a un client de `S5` pour
   verifier le routage multi-sauts.
4. Arreter `S2` via son admin console avec `STOP`.
5. Relancer `LIST_CONNECTIONS` sur `S1`, `S3`, `S4` et `S5`: `S1` doit encore
   pouvoir joindre `S4` et `S5` via `S3` apres convergence.
6. Arreter ensuite `S3`: `S1` ne doit plus avoir de route valide vers `S4` et
   `S5`, ce qui valide la propagation subtractive de `TOPOLOGY`.
