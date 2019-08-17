const writeStats = (compilation) => {
  const edges = [];

  for (let chunk of compilation.chunks) {
    for (let m of chunk._modules) {
      if (!m.buildInfo || !m.buildInfo.fileDependencies) { continue; }

      for (let file of m.buildInfo.fileDependencies) {
        for (let outputFile of chunk.files) {
          edges.push({ main: outputFile, require: file });
        }

        for (let reason of m.reasons) {
          if (!reason.module || !reason.module.buildInfo || !reason.module.buildInfo.fileDependencies) { continue; }

          for (let reasonFile of reason.module.buildInfo.fileDependencies) {
            edges.push({ main: reasonFile, require: file });
          }
        }
      }
    }
  }

  const s = JSON.stringify(edges);

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

