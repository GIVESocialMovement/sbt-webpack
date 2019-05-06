const writeStats = (compilation) => {
  const ms = [];

  for (let module of compilation.getStats().toJson().modules) {
    let reasons = [];

    for (let reason of module.reasons) {
      reasons.push(reason.moduleName);
    }

    ms.push({
      name: module.name,
      reasons: reasons
    })
  }

  compilation.chunks.map(chunk => ms.push({ name: chunk.id, reasons: '' }));
  const s = JSON.stringify(ms);

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


