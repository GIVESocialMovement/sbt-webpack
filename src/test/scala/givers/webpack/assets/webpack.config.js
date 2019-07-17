"use strict";

module.exports = {
  mode: 'development',
  module: {
    rules: [
      {
        test: /\.css$/i,
        use: ['css-loader'],
      },
      {
        test: /\.scss$/i,
        use: ['css-loader', 'sass-loader'],
      },
    ],
  },
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
