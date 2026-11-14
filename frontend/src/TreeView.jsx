/** Draws one node as a box, and its children as a row underneath (recursively). */
function TreeNode({ node }) {
  const hidden = node.keyCount - node.keys.length;
  return (
    <div className="tree-item">
      <div className={`tree-node ${node.leaf ? "leaf" : "internal"}`}>
        <div className="tree-page">
          page {node.page} · {node.keyCount} {node.keyCount === 1 ? "key" : "keys"}
        </div>
        <div className="tree-keys">
          {node.keys.join(", ")}
          {hidden > 0 && <span className="tree-more"> … +{hidden} more</span>}
        </div>
      </div>
      {!node.leaf && (
        <div className="tree-children">
          {node.children.map((child) => (
            <TreeNode key={child.page} node={child} />
          ))}
        </div>
      )}
    </div>
  );
}

export default function TreeView({ tree }) {
  return (
    <section className="tree">
      <h3>
        B+ tree on {tree.table}({tree.column})
      </h3>
      <p className="hint">
        {tree.entries} entries · height {tree.height} · blue = internal node (separators), green = leaf (real entries).
        Each box is one 4 KB page.
      </p>
      <div className="tree-scroll">
        <TreeNode node={tree.root} />
      </div>
    </section>
  );
}