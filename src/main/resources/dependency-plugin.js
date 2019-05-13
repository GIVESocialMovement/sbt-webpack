const writeStats = (compilation) => {
  const tree = [];

  for (const chunk of compilation.chunks) {
    const obj = {};
    const deps = [];

    for (const m of chunk._modules) {
      deps.push(m.id);
    }

    obj.name = chunk.files[0]; // After much research, I have yet figure out WHY this is in an array, but it is, and at least in our case it is always an array of 1
    obj.dependencies = deps;

    tree.push(obj);
  }

  const s = JSON.stringify(tree);

  compilation.assets['dependency-tree.json'] = {
    source() {
      return s;
    },
    size() {
      return s.length;
    }
  };
};

class DependencyPlugin {
  apply(compiler) {
    compiler.hooks.emit.tap("stat-emit", (compilation) => {
      writeStats(compilation);
  });
  }
}

module.exports = DependencyPlugin;

