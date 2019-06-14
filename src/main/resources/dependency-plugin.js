const writeStats = (compilation) => {
  const outputToDependencies = [];

  for (let chunk of compilation.chunks) {
    const deps = [];

    for (let m of chunk._modules) {
      deps.push(m.id);
    }

    outputToDependencies.push({
      output: chunk.files[0],
      dependencies: deps
    })
  }

  const s = JSON.stringify(outputToDependencies);
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