const MomentLocalesPlugin = require('moment-locales-webpack-plugin');

const baseConfig = {
  resolve: {
    extensions: ['.js', '.jsx'],
  },
  optimization: {
    splitChunks: {
      cacheGroups: {
        vendor: {
          test: /[\\/]node_modules[\\/]/,
          chunks: 'initial',
          name: 'vendor',
          enforce: true,
        }
      }
    },
  },
  plugins: [
    new MomentLocalesPlugin({
      localesToKeep: ['es-us'],
    }),
  ],
};

function configFactory(base) {
  if (process.env.NODE_ENV === 'production') {
    console.log('[sbt-webpack] Enable the production mode');

    base.mode = 'production';
  } else {
    console.log('[sbt-webpack] Enable the development mode');

    base.mode = 'development';
  }

  return base;
}

module.exports = configFactory(baseConfig);
