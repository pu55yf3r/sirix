package org.sirix.access.trx.node.json;

import org.sirix.access.trx.node.InternalNodeReadOnlyTrx;
import org.sirix.api.PageReadOnlyTrx;
import org.sirix.node.interfaces.StructNode;
import org.sirix.node.interfaces.immutable.ImmutableNode;

public interface InternalJsonNodeReadOnlyTrx extends InternalNodeReadOnlyTrx {
  ImmutableNode getCurrentNode();

  void setCurrentNode(ImmutableNode node);

  StructNode getStructuralNode();

  void assertNotClosed();

  void setPageReadTransaction(PageReadOnlyTrx trx);
}
