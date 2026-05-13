# Specification d'integration d'un nouveau contributeur

## Objectif du document

Ce document sert de guide d'integration pour une personne qui rejoint le projet
Notwork. Il doit lui permettre de comprendre rapidement le but du projet, son
architecture, les conventions importantes et les premieres taches pertinentes a
realiser sans casser le protocole ou le comportement reseau.

Le projet est un simulateur de reseau TCP/IP ecrit en Java 21 pour le cours
IN363. Il vise a illustrer la communication client/serveur, le routage entre
serveurs, l'administration d'un reseau de noeuds et la serialisation compacte de
messages au niveau bit.

## Profil attendu

La personne qui rejoint le projet doit pouvoir :

- lire et modifier du Java oriente objet ;
- comprendre les bases des sockets TCP ;
- manipuler un projet Maven simple ;
- raisonner sur des messages binaires dont les champs ne sont pas forcement
  alignes sur des octets ;
- travailler prudemment dans un programme multithread.

Une connaissance avancee des protocoles reseau n'est pas obligatoire, mais la
personne doit etre capable de suivre explicitement le chemin d'un message entre
un client, un serveur voisin et un autre client.

## Installation locale

Prerequis :

- Java 21 ;
- Maven ;
- un terminal capable de lancer plusieurs processus en parallele.

Depuis le dossier Maven `Notwork/`, la commande minimale de verification est :

```bash
mvn compile
```

Le projet n'a actuellement pas de suite de tests dediee. Toute modification de
comportement doit donc au minimum compiler et, si possible, etre validee par un
scenario manuel avec plusieurs serveurs et clients.

## Organisation du depot

Les fichiers et dossiers a connaitre en premier sont :

- `README.md` a la racine du depot : intention generale, architecture et
  description du format de message interne ;
- `Notwork/README.md` : commandes de build, arguments de lancement et etat du
  support du protocole universel ;
- `Notwork/src/main/java/com/gr15/Application.java` : point d'entree commun aux
  roles `admin`, `server` et `client` ;
- `Notwork/src/main/java/com/gr15/common/` : constantes, identifiants et classe
  `Message` pour la serialisation bit a bit ;
- `Notwork/src/main/java/com/gr15/common/message/` : messages types par sens de
  communication ;
- `Notwork/src/main/java/com/gr15/server/` : application serveur, connexions,
  handlers, managers et routage ;
- `Notwork/src/main/java/com/gr15/client/` : application cliente ;
- `Notwork/src/main/java/com/gr15/admin/` : application d'administration ;
- `docs/specification_reseau_universel.pdf` : specification externe du protocole
  serveur-a-serveur universel.

## Modele mental du systeme

L'application peut demarrer dans trois roles :

- `server` : accepte des clients, des consoles admin et des serveurs voisins ;
- `client` : se connecte a un serveur et envoie des messages a des clients
  connus ;
- `admin` : lance ou gere des serveurs/clients locaux et peut se connecter a une
  interface admin d'un serveur.

Le serveur est le coeur du systeme. Il est organise autour de `ServerApp` et de
trois managers :

- `ClientManager` pour les connexions client-serveur ;
- `ServerManager` pour les connexions serveur-serveur et la propagation reseau ;
- `AdminManager` pour les connexions admin-serveur.

Les threads bas niveau ne doivent pas modifier directement l'etat central du
serveur. Ils lisent les sockets, creent des evenements et les placent dans des
files. Les managers traitent ensuite ces files dans `pollEvents()`, appele par
la boucle principale de `ServerApp`.

Cette regle est structurante : lors d'une modification, il faut preserver le
decouplage entre lecture reseau, files d'evenements et mutation d'etat.

## Protocoles et messages

Le projet utilise deux couches de protocole :

- un protocole interne compact, represente par les messages `STC_*`, `CTS_*`,
  `STS_*`, `STA_*` et `ATS_*` ;
- un protocole universel pour les communications serveur-a-serveur, isole dans
  `com.gr15.common.message.universal`.

La classe `Message` ecrit et lit des champs avec un nombre exact de bits. Il ne
faut pas supposer que les donnees sont alignees sur des octets.

Invariants importants :

- l'ordre d'ecriture et l'ordre de lecture d'un message doivent etre strictement
  identiques ;
- les identifiants clients combinent un identifiant serveur et un identifiant
  local selon `Constants.SERVER_ID_BITS` et `Constants.LOCAL_ID_BITS` ;
- les TTL, identifiants de broadcast et sequences de routage utilisent les
  largeurs definies dans `Constants` ;
- les chaines sont encodees avec `Message.ENCODING_CHARSET`.

Pour ajouter un type de message :

1. ajouter l'entree dans la bonne enumeration (`MessageSTC`, `MessageCTS`,
   `MessageSTS`, `MessageSTA` ou `MessageATS`) ;
2. creer ou modifier la classe de message typee ;
3. garder `CreateMessage(...)` et `ReadMessage(...)` parfaitement symetriques ;
4. brancher le dispatch dans le handler ou manager concerne ;
5. compiler et verifier au moins un scenario manuel.

## Routage et propagation

Le routage serveur-a-serveur passe principalement par :

- `ServerManager` ;
- `ServerRoutingCoordinator` ;
- `RoutingTable` et `RoutingSnapshot` ;
- les messages `STS_RoutingUpdate`, `STS_RoutedMessage`,
  `STS_RoutedError` et `STS_BroadcastChat`.

Les broadcasts utilisent un identifiant et un TTL pour eviter les boucles. Toute
modification de routage doit conserver :

- la suppression des doublons ;
- la decrementation ou le respect du TTL ;
- la separation entre connexions serveur identifiees et connexions en attente ;
- la prevention des connexions dupliquees entre voisins.

## Scenarios de lancement utiles

Compiler :

```bash
mvn compile
```

Lancer deux serveurs voisins apres compilation directe avec `javac` :

```bash
java -cp /tmp/notwork-classes com.gr15.Application server serverId=1 clientport=2101 serverport=2102 adminport=2103 neighbor=2:localhost:2112
java -cp /tmp/notwork-classes com.gr15.Application server serverId=2 clientport=2111 serverport=2112 adminport=2113 neighbor=1:localhost:2102
```

Lancer un client :

```bash
java -cp /tmp/notwork-classes com.gr15.Application client hostname=localhost port=2101
```

Lancer une console admin :

```bash
java -cp /tmp/notwork-classes com.gr15.Application admin console=localhost:2103
```

Les ports valides sont controles par `ServerConfig.PORT_MIN` et
`ServerConfig.PORT_MAX`.

## Parcours de lecture recommande

Pour une premiere prise en main, lire dans cet ordre :

1. `README.md` a la racine du depot ;
2. `Notwork/README.md` ;
3. `Application.java` pour comprendre la selection du role ;
4. `Constants.java`, `ClientId.java` et `Message.java` ;
5. un exemple complet de message, par exemple `STC_MessageHello` ou
   `CTS_Message` ;
6. `ServerApp.java` pour la boucle principale ;
7. `ClientManager`, `ServerManager` et `AdminManager` ;
8. `UniversalPacketIO` et `UniversalMessageAdapter` si la tache touche le
   protocole universel.

## Premieres taches conseillees

Pour limiter le risque, les premieres contributions devraient etre petites et
observables :

- ameliorer un log existant avec `Logger` plutot qu'ajouter des impressions
  directes dans l'infrastructure ;
- documenter un message ou un scenario de lancement ;
- ajouter un test unitaire cible sur une classe pure comme `ClientId`,
  `BitmaskUtils`, `RoutingTable` ou une classe de message ;
- corriger une validation d'argument sans modifier le protocole ;
- ajouter un scenario manuel dans la documentation.

Les premieres taches a eviter sont :

- modifier `Message` sans test de non-regression ;
- changer les largeurs de bits dans `Constants` ;
- refondre les managers ou les threads ;
- modifier le routage broadcast sans scenario multi-serveur ;
- melanger protocole universel et protocole interne dans la meme abstraction.

## Definition of done

Une contribution est consideree terminee si :

- le code compile avec `mvn compile` depuis `Notwork/` ;
- les changements respectent le modele a files d'evenements des managers ;
- les messages restent lisibles/ecrits dans le meme ordre ;
- les logs sont explicites pour diagnostiquer un probleme reseau ;
- la documentation ou les tests couvrent le comportement ajoute si celui-ci est
  nouveau ;
- aucun fichier genere dans `target/` n'est ajoute au depot.

## Points de vigilance pour la revue

Lors d'une revue de code, verifier en priorite :

- les fermetures de sockets et l'arret des threads ;
- les acces concurrents aux collections de connexions ;
- les connexions serveur en attente versus identifiees ;
- la compatibilite des arguments compacts admin/client/serveur ;
- la conservation des identifiants client et serveur ;
- la symetrie stricte entre serialisation et deserialisation ;
- la preservation du TTL et de la detection des broadcasts deja vus.

## Critere d'autonomie

La personne est consideree autonome sur le projet lorsqu'elle peut :

- lancer localement un reseau de deux serveurs et deux clients ;
- expliquer le chemin d'un message client vers un client distant ;
- ajouter ou modifier un message simple sans casser la lecture bit a bit ;
- diagnostiquer une deconnexion de socket a partir des logs ;
- proposer une modification de routage en identifiant les risques de boucle,
  de doublon et de connexion concurrente.
