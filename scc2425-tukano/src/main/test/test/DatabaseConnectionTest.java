package test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class DatabaseConnectionTest {
    private static final String PERSISTENCE_UNIT_NAME = "mainPersistenceUnit";

    public static void main(String[] args) {
        EntityManagerFactory emf = null;
        EntityManager em = null;

        try {
            emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
            em = emf.createEntityManager();

            // Testa a conexão
            em.getTransaction().begin();
            em.createNativeQuery("SELECT 1").getSingleResult();
            em.getTransaction().commit();

            System.out.println("Conexão bem-sucedida ao banco de dados!");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Falha na conexão ao banco de dados.");
        } finally {
            if (em != null) em.close();
            if (emf != null) emf.close();
        }
    }
}

