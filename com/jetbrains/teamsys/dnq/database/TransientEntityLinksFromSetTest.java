package com.jetbrains.teamsys.dnq.database;

import com.jetbrains.mps.dnq.common.tests.AbstractEntityStoreAwareTestCase;
import com.jetbrains.mps.dnq.common.tests.TestOnlyServiceLocator;
import com.jetbrains.teamsys.dnq.association.AssociationSemantics;
import com.jetbrains.teamsys.dnq.association.DirectedAssociationSemantics;
import com.jetbrains.teamsys.dnq.association.PrimitiveAssociationSemantics;
import jetbrains.exodus.core.dataStructures.NanoSet;
import jetbrains.exodus.core.dataStructures.hash.HashSet;
import jetbrains.exodus.database.*;
import jetbrains.exodus.database.async.EntityStoreSharedAsyncProcessor;
import jetbrains.exodus.database.impl.iterate.EntityIteratorWithPropId;

public class TransientEntityLinksFromSetTest extends AbstractEntityStoreAwareTestCase {

    public void testAll() {
        TransientEntity i1, i2, i3, i4;
        TransientEntityStore store = TestOnlyServiceLocator.getTransientEntityStore();
        TransientStoreSession session = store.beginSession();
        try {
            i1 = createIssue(session);
            i2 = createIssue(session);
            i3 = createIssue(session);
            i4 = createIssue(session);
        } catch (Throwable e) {
            TransientStoreUtil.abort(e, session);
            throw new RuntimeException("Should never be thrown.");
        } finally {
            TransientStoreUtil.commit(session);
        }

        final HashSet<String> names = new HashSet<String>();
        names.add("dup");
        names.add("hup");

        session = store.beginSession();
        try {
            DirectedAssociationSemantics.createToMany(i1, "dup", i2);
            DirectedAssociationSemantics.createToMany(i1, "hup", i3);
            DirectedAssociationSemantics.createToMany(i1, "hup", i4);
            DirectedAssociationSemantics.createToMany(i2, "dup", i3);

            check_i1(AssociationSemantics.getAddedLinks(i1, names), i2, i3, i4);
            check_i2(AssociationSemantics.getAddedLinks(i2, names), i3);

            check_i1(AssociationSemantics.getAddedLinks(ro(i1, session), names), i2, i3, i4);
            check_i2(AssociationSemantics.getAddedLinks(ro(i2, session), names), i3);

            assertFalse(AssociationSemantics.getRemovedLinks(i1, names).iterator().hasNext());
            assertFalse(AssociationSemantics.getRemovedLinks(i2, names).iterator().hasNext());
        } catch (Throwable e) {
            TransientStoreUtil.abort(e, session);
            throw new RuntimeException("Should never be thrown.");
        } finally {
            TransientStoreUtil.commit(session);
        }

        session = store.beginSession();
        try {
            check_i1(AssociationSemantics.getToMany(i1, names), i2, i3, i4);
            check_i2(AssociationSemantics.getToMany(i2, names), i3);
        } catch (Throwable e) {
            TransientStoreUtil.abort(e, session);
            throw new RuntimeException("Should never be thrown.");
        } finally {
            TransientStoreUtil.commit(session);
        }

        session = store.beginSession();
        try {
            DirectedAssociationSemantics.removeToMany(i1, "dup", i2);
            DirectedAssociationSemantics.removeToMany(i1, "hup", i3);
            DirectedAssociationSemantics.removeToMany(i1, "hup", i4);
            DirectedAssociationSemantics.removeToMany(i2, "dup", i3);

            check_i1(AssociationSemantics.getRemovedLinks(i1, names), i2, i3, i4);
            check_i2(AssociationSemantics.getRemovedLinks(i2, names), i3);

            check_i1(AssociationSemantics.getRemovedLinks(ro(i1, session), names), i2, i3, i4);
            check_i2(AssociationSemantics.getRemovedLinks(ro(i2, session), names), i3);

            assertFalse(AssociationSemantics.getAddedLinks(i1, names).iterator().hasNext());
            assertFalse(AssociationSemantics.getAddedLinks(i2, names).iterator().hasNext());
        } catch (Throwable e) {
            TransientStoreUtil.abort(e, session);
            throw new RuntimeException("Should never be thrown.");
        } finally {
            TransientStoreUtil.commit(session);
        }
    }

    private static TransientEntity createIssue(TransientStoreSession session) {
        return (TransientEntity) session.newEntity("Issue");
    }

    private static TransientEntity ro(TransientEntity i1, TransientStoreSession session) {
        return session.newReadonlyLocalCopy(session.getTransientChangesTracker().getChangeDescription(i1));
    }

    private void check_i2(Iterable<Entity> iterable, TransientEntity i3) {
        final EntityIteratorWithPropId it = (EntityIteratorWithPropId)iterable.iterator();
        assertTrue(it.hasNext());
        assertEquals(i3, it.next());
        assertEquals("dup", it.currentLinkName());
        assertFalse(it.hasNext());
    }

    private void check_i1(Iterable<Entity> iterable, TransientEntity i2, TransientEntity i3, TransientEntity i4) {
        final EntityIteratorWithPropId it = (EntityIteratorWithPropId)iterable.iterator();
        assertTrue(it.hasNext());
        assertEquals(i2, it.next());
        assertEquals("dup", it.currentLinkName());
        assertTrue(it.hasNext());
        final Entity candidate = it.next();
        assertEquals("hup", it.currentLinkName());
        assertTrue(it.hasNext());
        if (i3.equals(candidate)) { // handle set nondeterminism
            assertEquals(i4, it.next());
        } else {
            assertEquals(i4, candidate);
            assertEquals(i3, it.next());
        }
        assertEquals("hup", it.currentLinkName());
        assertFalse(it.hasNext());
    }
}
