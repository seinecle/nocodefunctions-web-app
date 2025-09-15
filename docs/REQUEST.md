# Fichiers à ajouter pour refactorer le flux Umigon

Pour refactorer Umigon comme les autres flows (DataSource + State sous `...flows.umigon`), j’aurai probablement besoin de modifier les fichiers suivants déjà présents dans votre dépôt. Merci de les ajouter à la discussion:

- src\main\java\net\clementlevallois\nocodeapp\web\front\backingbeans\UmigonBean.java
  (le managed bean/contrôleur actuel qui pilote la fonctionnalité Umigon)

- src\main\webapp\umigon.xhtml
  (ou la/les pages XHTML liées à Umigon; si elles sont ailleurs, ajoutez celles correspondantes)

- src\main\java\net\clementlevallois\nocodeapp\web\front\http\UmigonClient.java
  (s’il existe un client HTTP spécifique à Umigon)

Remarque:
- Je créerai moi-même les nouveaux fichiers sous `src\main\java\net\clementlevallois\nocodeapp\web\front\flows\umigon\` (UmigonDataSource.java, UmigonState.java).
- Aucun autre fichier ne devrait nécessiter de modification au départ.
