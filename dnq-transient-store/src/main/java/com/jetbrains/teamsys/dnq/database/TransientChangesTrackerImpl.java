/**
 * Copyright 2006 - 2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.teamsys.dnq.database;

import jetbrains.exodus.core.dataStructures.Pair;
import jetbrains.exodus.core.dataStructures.decorators.HashMapDecorator;
import jetbrains.exodus.core.dataStructures.decorators.LinkedHashSetDecorator;
import jetbrains.exodus.core.dataStructures.hash.HashMap;
import jetbrains.exodus.core.dataStructures.hash.HashSet;
import jetbrains.exodus.core.dataStructures.hash.LinkedHashSet;
import jetbrains.exodus.database.*;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.PersistentStoreTransaction;
import jetbrains.exodus.entitystore.ReadOnlyPersistentEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Vadim.Gurov
 */
public final class TransientChangesTrackerImpl implements TransientChangesTracker {

    @NotNull
    private final Set<TransientEntity> changedEntities = new LinkedHashSet<TransientEntity>();
    @NotNull
    private final Set<TransientEntity> addedEntities = new LinkedHashSet<TransientEntity>();
    @NotNull
    private final Set<TransientEntity> removedEntities = new LinkedHashSetDecorator<TransientEntity>();
    @NotNull
    private final Set<String> affectedEntityTypes = new HashSet<String>();
    @NotNull
    private final Map<TransientEntity, List<LinkChange>> removedFrom = new HashMapDecorator<TransientEntity, List<LinkChange>>();
    @NotNull
    private final Map<TransientEntity, Map<String, LinkChange>> entityToChangedLinksDetailed = new HashMapDecorator<TransientEntity, Map<String, LinkChange>>();
    @NotNull
    private final Map<TransientEntity, Set<String>> entityToChangedProperties = new HashMapDecorator<TransientEntity, Set<String>>();
    @Nullable
    private PersistentStoreTransaction snapshot;

    TransientChangesTrackerImpl(@NotNull PersistentStoreTransaction snapshot) {
        this.snapshot = snapshot;
    }

    @NotNull
    public Set<TransientEntity> getChangedEntities() {
        return changedEntities;
    }

    @NotNull
    public Set<String> getAffectedEntityTypes() {
        return Collections.unmodifiableSet(affectedEntityTypes);
    }

    @NotNull
    public PersistentStoreTransaction getSnapshot() {
        PersistentStoreTransaction snapshot = this.snapshot;
        if (snapshot == null) {
            throw new IllegalStateException("Cannot get persistent store transaction because changes tracker is already disposed");
        }
        return snapshot;
    }

    @Override
    @NotNull
    public TransientEntityImpl getSnapshotEntity(@NotNull TransientEntity e) {
        final ReadOnlyPersistentEntity ro = e.getPersistentEntity().getSnapshot(getSnapshot());
        return new ReadonlyTransientEntityImpl(getChangeDescription(e), ro, e.getStore());
    }

    @NotNull
    public Set<TransientEntityChange> getChangesDescription() {
        Set<TransientEntityChange> changesDescription = new LinkedHashSetDecorator<TransientEntityChange>();

        for (TransientEntity e : getChangedEntities()) {
            // do not notify about RemovedNew entities - such entities was created and removed during same transaction
            if (wasCreatedAndRemovedInSameTransaction(e)) continue;

            changesDescription.add(new TransientEntityChange(this, e, getChangedProperties(e), getChangedLinksDetailed(e), getEntityChangeType(e)));
        }

        return changesDescription;
    }

    @Override
    public int getChangesDescriptionCount() {
        int addedAndRemovedCount = 0;
        for (TransientEntity removed : removedEntities) {
            if (addedEntities.contains(removed)) {
                addedAndRemovedCount++;
            }
        }
        return changedEntities.size() - addedAndRemovedCount;
    }

    private EntityChangeType getEntityChangeType(TransientEntity e) {
        if (addedEntities.contains(e)) return EntityChangeType.ADD;
        if (removedEntities.contains(e)) return EntityChangeType.REMOVE;
        return EntityChangeType.UPDATE;
    }

    @NotNull
    public TransientEntityChange getChangeDescription(@NotNull TransientEntity e) {
        return new TransientEntityChange(this, e, getChangedProperties(e), getChangedLinksDetailed(e), getEntityChangeType(e));
    }

    @Nullable
    public Map<String, LinkChange> getChangedLinksDetailed(@NotNull TransientEntity e) {
        return entityToChangedLinksDetailed.get(e);
    }

    @Nullable
    public Set<String> getChangedProperties(@NotNull TransientEntity e) {
        return entityToChangedProperties.get(e);
    }

    @NotNull
    public Set<TransientEntity> getRemovedEntities() {
        return removedEntities;
    }

    public Set<TransientEntity> getAddedEntities() {
        return addedEntities;
    }

    public boolean isNew(@NotNull TransientEntity e) {
        return addedEntities.contains(e);
    }

    public boolean isRemoved(@NotNull TransientEntity e) {
        return removedEntities.contains(e);
    }

    public boolean isSaved(@NotNull TransientEntity e) {
        return !addedEntities.contains(e) && !removedEntities.contains(e);
    }

    boolean wasCreatedAndRemovedInSameTransaction(@NotNull TransientEntity e) {
        return addedEntities.contains(e) && removedEntities.contains(e);
    }

    public void linksRemoved(@NotNull TransientEntity source, @NotNull String linkName, Iterable<Entity> links) {
        entityChanged(source);

        final Pair<Map<String, LinkChange>, LinkChange> lc = getLinkChange(source, linkName);
        for (Entity entity : links) {
            addRemoved(lc.getSecond(), (TransientEntity) entity);
        }
    }

    private Pair<Map<String, LinkChange>, LinkChange> getLinkChange(TransientEntity source, String linkName) {
        Map<String, LinkChange> linksDetailed = entityToChangedLinksDetailed.get(source);
        if (linksDetailed == null) {
            linksDetailed = new HashMap<String, LinkChange>();
            entityToChangedLinksDetailed.put(source, linksDetailed);
        }

        LinkChange lc = linksDetailed.get(linkName);
        if (lc == null) {
            lc = new LinkChange(linkName);
            linksDetailed.put(linkName, lc);
        }

        return new Pair<Map<String, LinkChange>, LinkChange>(linksDetailed, lc);
    }

    public void linkChanged(@NotNull TransientEntity source, @NotNull String linkName, @NotNull TransientEntity target, @Nullable TransientEntity oldTarget, boolean add) {
        entityChanged(source);

        final Pair<Map<String, LinkChange>, LinkChange> pair = getLinkChange(source, linkName);
        final LinkChange lc = pair.getSecond();
        if (add) {
            if (oldTarget != null) {
                addRemoved(lc, oldTarget);
            }
            lc.addAdded(target);
        } else {
            addRemoved(lc, target);
        }
        if (lc.getAddedEntitiesSize() == 0 && lc.getRemovedEntitiesSize() == 0 && lc.getDeletedEntitiesSize() == 0) {
            pair.getFirst().remove(linkName);
            if (pair.getFirst().size() == 0) {
                entityToChangedLinksDetailed.remove(source);
            }
        }
    }

    private void addRemoved(@NotNull final LinkChange change, @NotNull final TransientEntity entity) {
        change.addRemoved(entity);
        List<LinkChange> changes = removedFrom.get(entity);
        if (changes == null) {
            changes = new ArrayList<LinkChange>();
            removedFrom.put(entity, changes);
        }
        changes.add(change);
    }

    void entityChanged(TransientEntity e) {
        changedEntities.add(e);
        affectedEntityTypes.add(e.getType());
    }

    public void propertyChanged(TransientEntity e, String propertyName) {
        entityChanged(e);

        Set<String> properties = entityToChangedProperties.get(e);
        if (properties == null) {
            properties = new HashSet<String>();
            entityToChangedProperties.put(e, properties);
        }

        properties.add(propertyName);
    }

    public void removePropertyChanged(TransientEntity e, String propertyName) {
        Set<String> properties = entityToChangedProperties.get(e);
        if (properties != null) {
            properties.remove(propertyName);
            if (properties.isEmpty()) {
                entityToChangedProperties.remove(e);
            }
        }
    }

    public void entityAdded(TransientEntity e) {
        entityChanged(e);
        addedEntities.add(e);
    }

    public void entityRemoved(TransientEntity e) {
        entityChanged(e);
        removedEntities.add(e);
        List<LinkChange> changes = removedFrom.get(e);
        if (changes != null) {
            for (LinkChange change : changes) {
                change.addDeleted(e);
            }
        }
    }

    @Override
    @NotNull
    public TransientChangesTracker upgrade() {
        return this;
    }

    @Override
    public void dispose() {
        if (snapshot != null) {
            snapshot.abort();
            snapshot = null;
        }
    }
}
