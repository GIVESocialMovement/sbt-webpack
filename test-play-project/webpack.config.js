"use strict";

module.exports = {
  module: {
    rules: [
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
          options: 'Vue'
        }
      },
      {
        test: /axios\.js/,
        use: {
          loader: 'expose-loader',
          options: 'axios'
        }
      },
      {
        test: /vue-i18n\.js/,
        use: {
          loader: 'expose-loader',
          options: 'VueI18n'
        }
      }
    ],
  },
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
  devtool: ''
};

if (process.env.NODE_ENV === 'production') {
  console.log('[sbt-webpack] Enable the production mode');
  module.exports.mode = 'production';
} else {
  console.log('[sbt-webpack] Enable the development mode');
  module.exports.mode = 'development';
}