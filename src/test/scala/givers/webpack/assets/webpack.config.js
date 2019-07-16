"use strict";

module.exports = {
  mode: 'development',
  optimization: {
    splitChunks: {
      cacheGroups: {
        vendor: {
          test: /vendor/,
          chunks: 'initial',
          name: 'dist/vendor.js',
          enforce: true,
        }
      }
    },
  },
  devtool: 'source-map'
};
