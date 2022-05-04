"use strict";

const VueLoaderPlugin = require('vue-loader/lib/plugin')

module.exports = {
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
      {
        test: /\.vue$/,
        loader: 'vue-loader'
      },
      {
        test: /\.js$/,
        exclude: /(node_modules|bower_components)/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: ['@babel/preset-env']
          }
        }
      },
      {
        test: /vue\.runtime\.js/,
        use: {
          loader: 'expose-loader',
          options: {
            exposes: 'Vue'
          }
        }
      },
      {
        test: /axios\.js/,
        use: {
          loader: 'expose-loader',
          options: {
            exposes: 'axios'
          }
        }
      },
      {
        test: /vue-i18n\.js/,
        use: {
          loader: 'expose-loader',
          options: {
            exposes: 'VueI18n'
          }
        }
      }
    ],
  },
  plugins: [
    new VueLoaderPlugin()
  ],
  optimization: {
    splitChunks: {
      cacheGroups: {
        vendor: {
          test: /app\/assets\/javascripts\/vendor/,
          chunks: 'initial',
          name: 'javascripts/vendor.js',
          enforce: true,
        },
        node_modules: {
          test: /node_modules/,
          chunks: 'initial',
          name: 'javascripts/node_modules.js',
          enforce: true,
        }
      }
    },
  },
  devtool: 'source-map'
};

if (process.env.NODE_ENV === 'production') {
  console.log('[sbt-webpack] Enable the production mode');
  module.exports.mode = 'production';
} else {
  console.log('[sbt-webpack] Enable the development mode');
  module.exports.mode = 'development';
}