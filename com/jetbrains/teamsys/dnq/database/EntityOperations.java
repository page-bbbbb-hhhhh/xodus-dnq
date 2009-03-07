package com.jetbrains.teamsys.dnq.database;

import com.jetbrains.teamsys.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

// TODO: move this class to the associations semantics package
public class EntityOperations {

  private static final Log log = LogFactory.getLog(EntityOperations.class);

  private EntityOperations() {
  }

  public static void remove(Entity e) {
    if (e == null || ((TransientEntity) e).isRemoved()) {
      return;
    }

    e = TransientStoreUtil.reattach((TransientEntity) e);
    TransientEntityStore store = (TransientEntityStore) e.getStore();

    ModelMetaData md = store.getModelMetaData();
    if (md != null) {
      // cascade delete
      EntityMetaData emd = md.getEntityMetaData(e.getType());
      if (emd != null) {
        md.getEntityMetaData(e.getType()).executeDestructor(e);

        // remove associations and cascade delete 
        ConstraintsUtil.processOnDeleteConstraints(store.getThreadSession(), e, emd, md);
      }
    }

    // delete itself; the check is performed, because onDelete constraints could already delete entity 'e'
    if (!((TransientEntity) e).isRemoved()) e.delete();
  }

  public static List<Entity> getHistory(@NotNull Entity e) {
    e = TransientStoreUtil.reattach((TransientEntity) e);

    return e == null ? Collections.EMPTY_LIST : e.getHistory();
  }

  public static boolean isRemoved(@NotNull Entity e) {
    return e == null || ((TransientEntity)e).isRemoved() || TransientStoreUtil.reattach((TransientEntity) e) == null;
  }

  public static int getVersion(@NotNull Entity e) {
    e = TransientStoreUtil.reattach((TransientEntity) e);

    return e == null ? -1 : e.getVersion();
  }

  public static Entity getPreviousVersion(@NotNull Entity e) {
    e = TransientStoreUtil.reattach((TransientEntity) e);

    return e == null ? null : e.getPreviousVersion();
  }

  public static Entity getNextVersion(@NotNull Entity e) {
    e = TransientStoreUtil.reattach((TransientEntity) e);

    return e == null ? null : e.getNextVersion();
  }

  public static boolean equals(Entity e1, Object e2) {
    if (e1 == null && e2 == null) {
      return true;
    }

    if (e1 == e2) {
      return true;
    }

    if (e1 == null || !(e2 instanceof Entity)) {
      return false;
    }

    //no need to reattach - it's ok to compare entities from different sessions, Entity.equals should handle this situation itself 
    //e1 = TransientStoreUtil.reattach((TransientEntity) e1);
    //e2 = TransientStoreUtil.reattach((TransientEntity) e2);

    return e1.equals(e2);
  }

  /**
   * Slow method! Use with care.
   *
   * @param entities
   * @param i
   * @return
   * @deprecated slow method. for testcases only.
   */
  public static Entity getElement(@NotNull Iterable<Entity> entities, int i) {
    if (log.isWarnEnabled()) {
      log.warn("Slow method getElementOfMultiple() was called!");
    }

    if (entities instanceof EntityIterable) {
      final EntityIterator it = ((EntityIterable) entities).skip(i).iterator();
      if (it.hasNext()) {
        return it.next();
      }
    } else {
      int j = 0;
      for (Entity e : entities) {
        if (i == j++) {
          return e;
        }
      }
    }

    throw new IllegalArgumentException("Out of bounds: " + i);
  }

  public static boolean hasChanges(@NotNull TransientEntity e) {
    e = TransientStoreUtil.reattach(e);

    return e == null ? false : e.hasChanges();
  }

  public static boolean hasChanges(@NotNull TransientEntity e, String property) {
    e = TransientStoreUtil.reattach(e);

    return e == null ? false : e.hasChanges(property);
  }
}
