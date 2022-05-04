const fs = require('fs');

/**
 * scans file system snapshots recursively
 * In webpack 5 it's a replacement of module.buildInfo.fileDependencies that was available in webpack 4
 * but in webpack 5 it's always empty. See webpack/lib/NormalModule.js link that contains "compilation.fileSystemInfo.createSnapshot"
 */
const scanSnapshotFiles = (snapshot) => {
  let files = Array
    .from(snapshot.getFileIterable())
    .filter(path => fs.lstatSync(path).isFile() && !path.includes("package.json")) || [];

  if(snapshot.children) {
    snapshot.children.forEach(child => {
      files = files.concat(scanSnapshotFiles(child));
    });
  }

  return files;
};

const writeStats = (compilation) => {
  const edges = [];

  compilation.chunks.forEach((chunk) => {
    chunk.getModules().filter(module => module.buildInfo && module.buildInfo.snapshot).forEach((module) => {
      scanSnapshotFiles(module.buildInfo.snapshot).forEach(file => {
        chunk.files.forEach(outputFile => {
          edges.push({ main: outputFile, require: file });
        });

        /**
         * auxiliaryFiles usually contains .map files.
         * In webpack 5 we used module.reasons but in webpack 5 it's not available
         * See https://webpack.js.org/blog/2020-10-10-webpack-5-release/#other-minor-changes
         */
        chunk.auxiliaryFiles.forEach(reasonFile => {
          edges.push({ main: reasonFile, require: file });
        });
      })
    });
  });

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
