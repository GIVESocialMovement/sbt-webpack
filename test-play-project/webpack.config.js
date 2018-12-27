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
  devtool: ''
};
