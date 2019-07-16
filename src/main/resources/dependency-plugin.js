const writeStats = (compilation) => {
  const outputToDependencies = [];

  for (let chunk of compilation.chunks) {
    const deps = [];

    for (let m of chunk._modules) {
      if (m.buildInfo && m.buildInfo.fileDependencies) {
        for (let dep of m.buildInfo.fileDependencies) {
          deps.push(dep);
        }
      }
    }

    for (let file of chunk.files) {
      outputToDependencies.push({
        output: file,
        dependencies: deps
      })
    }
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