const writeStats = (compilation) => {
  const output_file = [];

  for (const chunk of compilation.chunks) {
    const deps = [];

    for (const m of chunk._modules) {
      deps.push(m.id);
    }

    output_file.push({
      name: chunk.files, // After much research, I have yet figure out WHY this is in an array, but it is, and at least in our case it is always an array of 1
      dependencies: deps,
    });
  }

  const s = JSON.stringify(output_file);

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

