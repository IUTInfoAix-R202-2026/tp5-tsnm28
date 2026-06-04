package fr.univ_amu.iut.exercice6;

import fr.univ_amu.iut.jdbc.DataAccessException;

//import java.beans.Statement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;

/**
 * Service d'import d'un passage et de ses observations (exercice 6) :
 * illustration des
 * <b>transactions</b>.
 *
 * <p>
 * Importer un passage et ses observations, c'est plusieurs écritures (un
 * {@code INSERT} dans
 * {@code passage}, puis un {@code INSERT} par observation). Il faut que ce soit
 * <b>tout ou rien</b>
 * : si une observation échoue (par exemple parce que son taxon n'existe pas),
 * on ne veut pas d'un
 * passage à moitié importé. C'est le rôle d'une transaction : on désactive
 * l'auto-commit, on
 * exécute toutes les écritures, on {@code commit()} à la fin ; au moindre
 * problème, on {@code
 * rollback()} pour tout annuler.
 */
public class ImportPassageService {

  private final DataSource source;

  public ImportPassageService(DataSource source) {
    this.source = source;
  }

  /**
   * Importe un passage et ses observations de façon atomique.
   *
   * @return l'identifiant généré du passage inséré
   * @throws DataAccessException si l'import échoue (la base est alors laissée
   *                             intacte)
   */
  public long importer(
      String numeroCarre,
      String codePoint,
      int numeroPassage,
      int annee,
      List<ObservationAImporter> observations) {

    String sqlPassage = "INSERT INTO passage"
        + " (numero_carre, code_point, numero_passage, annee, statut_workflow)"
        + " VALUES (?, ?, ?, ?, 'Importé')";
    String sqlObservation = "INSERT INTO observation"
        + " (passage_id, temps_debut, temps_fin, frequence_mediane, code_taxon, probabilite)"
        + " VALUES (?, ?, ?, ?, ?, ?)";

    long passageId = -1;

    // TODO exercice 6 : réaliser l'import dans une transaction et renseigner
    // `passageId`.
    //
    // 1. Ouvrir une connexion, puis connexion.setAutoCommit(false).
    Connection connexion = null;

    // 2. Dans un try :
    // - insérer le passage (prepareStatement(sqlPassage,
    // Statement.RETURN_GENERATED_KEYS),
    // positionner les paramètres, executeUpdate) ;
    // - récupérer l'id généré : keys.next(); passageId = keys.getLong(1) ;
    // - pour chaque observation, l'insérer avec ce passageId ;
    // - connexion.commit().
    try {
      connexion = source.getConnection();
      connexion.setAutoCommit(false);
      try (PreparedStatement ps = connexion.prepareStatement(sqlPassage, java.sql.Statement.RETURN_GENERATED_KEYS)) {
        ps.setString(1, numeroCarre);
        ps.setString(2, codePoint);
        ps.setInt(3, numeroPassage);
        ps.setInt(4, annee);
        ps.executeUpdate();

        try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
          if (generatedKeys.next()) {
            passageId = generatedKeys.getLong(1);
          } else {
            throw new SQLException("message");
          }
        }

      }
      try (PreparedStatement ps = connexion.prepareStatement(sqlObservation)) {
        for (ObservationAImporter observation : observations) {
          ps.setLong(1, passageId);
          ps.setDouble(2, observation.tempsDebut());
          ps.setDouble(3, observation.tempsFin());
          ps.setInt(4, observation.frequenceMediane());
          ps.setString(5, observation.codeTaxon());
          ps.setDouble(6, observation.probabilite());
          ps.executeUpdate();
        }
      }

      connexion.commit();

      // 3. catch (SQLException) : connexion.rollback() puis lever une
      // DataAccessException.
    } catch (SQLException e) {
      annulerSilencieusement(connexion);
      throw new DataAccessException("message", e);
    }
    // 4. finally : refermer la connexion.
    //
    finally {
      fermerSilencieusement(connexion);
    }
    // Astuce : ouvrez la connexion AVANT le try afin de pouvoir faire rollback dans
    // le catch.
    return passageId;
  }

  /**
   * Nombre de passages en base (fourni, utile pour vérifier qu'un rollback a bien
   * tout annulé).
   */
  public int nombrePassages() {
    try (Connection connexion = source.getConnection();
        PreparedStatement ps = connexion.prepareStatement("SELECT COUNT(*) FROM passage");
        ResultSet rs = ps.executeQuery()) {
      rs.next();
      return rs.getInt(1);
    } catch (SQLException e) {
      throw new DataAccessException("Impossible de compter les passages", e);
    }
  }

  private static void annulerSilencieusement(Connection connexion) {
    if (connexion != null) {
      try {
        connexion.rollback();
      } catch (SQLException ignore) {
        // rien à faire : on remonte déjà l'exception d'origine
      }
    }
  }

  private static void fermerSilencieusement(Connection connexion) {
    if (connexion != null) {
      try {
        connexion.close();
      } catch (SQLException ignore) {
        // connexion déjà fermée ou inutilisable
      }
    }
  }
}
