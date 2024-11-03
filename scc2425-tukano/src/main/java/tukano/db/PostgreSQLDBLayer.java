package tukano.db;

import java.util.List;
import java.util.function.Supplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;

public class PostgreSQLDBLayer {
    private static final String PERSISTENCE_UNIT_NAME = "mainPersistenceUnit";
    
    private static PostgreSQLDBLayer instance;
    private EntityManagerFactory emf;

    public static synchronized PostgreSQLDBLayer getInstance() {
        if (instance != null)
            return instance;

        instance = new PostgreSQLDBLayer();
        return instance;
    }

    private PostgreSQLDBLayer() {
        this.emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
    }

    private EntityManager getEntityManager() {
        return emf.createEntityManager();
    }

    public void close() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    // User functions
    public <T> Result<T> getUser(String id, Class<T> clazz) {
        return tryCatch(() -> {
            EntityManager em = getEntityManager();
            try {
                return em.find(clazz, id);
            } finally {
                em.close();
            }
        });
    }

    public <T> Result<?> deleteUser(T obj) {
        return tryCatch(() -> {
            EntityManager em = getEntityManager();
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                em.remove(em.contains(obj) ? obj : em.merge(obj));
                tx.commit();
                return obj;
            } finally {
                if (tx.isActive()) tx.rollback();
                em.close();
            }
        });
    }

    public <T> Result<T> updateUser(T obj) {
        return tryCatch(() -> {
            EntityManager em = getEntityManager();
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                T mergedObj = em.merge(obj);
                tx.commit();
                return mergedObj;
            } finally {
                if (tx.isActive()) tx.rollback();
                em.close();
            }
        });
    }

    public <T> Result<T> insertUser(T obj) {
        return tryCatch(() -> {
            EntityManager em = getEntityManager();
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                em.persist(obj);
                tx.commit();
                return obj;
            } finally {
                if (tx.isActive()) tx.rollback();
                em.close();
            }
        });
    }

    public <T> Result<List<T>> queryUsers(Class<T> clazz, String queryStr) {
        return tryCatch(() -> {
            EntityManager em = getEntityManager();
            try {
                TypedQuery<T> query = em.createQuery(queryStr, clazz);
                return query.getResultList();
            } finally {
                em.close();
            }
        });
    }

    // Shorts functions
    public <T> Result<T> getShort(String id, Class<T> clazz) {
        return tryCatch(() -> {
            EntityManager em = getEntityManager();
            try {
                return em.find(clazz, id);
            } finally {
                em.close();
            }
        });
    }

    public <T> Result<?> deleteShort(T obj) {
        return tryCatch(() -> {
            EntityManager em = getEntityManager();
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                em.remove(em.contains(obj) ? obj : em.merge(obj));
                tx.commit();
                return obj;
            } finally {
                if (tx.isActive()) tx.rollback();
                em.close();
            }
        });
    }

    public <T> Result<T> updateShort(T obj) {
        return tryCatch(() -> {
            EntityManager em = getEntityManager();
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                T mergedObj = em.merge(obj);
                tx.commit();
                return mergedObj;
            } finally {
                if (tx.isActive()) tx.rollback();
                em.close();
            }
        });
    }

    public <T> Result<T> insertShort(T obj) {
        return tryCatch(() -> {
            EntityManager em = getEntityManager();
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                em.persist(obj);
                tx.commit();
                return obj;
            } finally {
                if (tx.isActive()) tx.rollback();
                em.close();
            }
        });
    }

    public <T> Result<List<T>> queryShorts(Class<T> clazz, String queryStr) {
        return tryCatch(() -> {
            EntityManager em = getEntityManager();
            try {
                TypedQuery<T> query = em.createQuery(queryStr, clazz);
                return query.getResultList();
            } finally {
                em.close();
            }
        });
    }

    <T> Result<T> tryCatch(Supplier<T> supplierFunc) {
        try {
            return Result.ok(supplierFunc.get());
        } catch (NoResultException nre) {
            return Result.error(ErrorCode.NOT_FOUND);
        } catch (Exception x) {
            x.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    static Result.ErrorCode errorCodeFromStatus(int status) {
        return switch (status) {
            case 200 -> ErrorCode.OK;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}

