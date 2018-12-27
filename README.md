sbt-webpack
=============

[![CircleCI](https://circleci.com/gh/GIVESocialMovement/sbt-webpack/tree/master.svg?style=shield)](https://circleci.com/gh/GIVESocialMovement/sbt-webpack/tree/master)
[![codecov](https://codecov.io/gh/GIVESocialMovement/sbt-webpack/branch/master/graph/badge.svg)](https://codecov.io/gh/GIVESocialMovement/sbt-webpack)
[![Gitter chat](https://badges.gitter.im/GIVE-asia/gitter.png)](https://gitter.im/GIVE-asia/Lobby)

`sbt-webpack` integrates [Webpack 4](https://webpack.js.org) with Playframework assets' incremental compilation. 
This plugin also tracks JS dependencies correctly (e.g. using `require` or `import` in a JS file).

`sbt-webpack` is currently used at GIVE.asia. We are using it for packaging multiple JS files into one
 and exposing certain variables using [expose-loader](https://github.com/webpack-contrib/expose-loader).
 
Please see a working example in `test-play-project`.


### Why do we need `sbt-webpack`?

The problem arises when we want to compile some JS files with Webpack and use the compiled JS within Playframework.

Without the plugin, we would have to run `webpack watch` separately and specify the output location of the compiled JS file correctly, so Playframework's routing can find the compiled JS file.

Playframework already has its own "watch" mechanism and offers a good way to store the compiled JS (so it works with Playfraemework's routing). This plugin does exactly that.

 
Requirement
------------

* Yarn or NPM
* Webpack 4
* Playframework 2.6
 
 
Usage
------

### 1. Install

Add the below lines to `project/plugins.sbt`:

```
resolvers += Resolver.bintrayRepo("givers", "maven")

addSbtPlugin("givers.webpack" % "sbt-webpack" % "0.1.0")
```


### 2. Create `webpack.config.js`.

Here's a minimal example:

```
module.exports = {};
```

Please do not specify `entry` and `output`. They are added automatically by `sbt-webpack`.


### 3. Configure `build.sbt`

Configure `sbt-webpack` and specify Webpack's entry points on `build.sbt`:

```
lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb, SbtWebpack) // Enable the plugin

// The commands that triggers production build (as in `webpack -p`)
Assets / WebpackKeys.vuefy / WebpackKeys.prodCommands := Set("stage")

Assets / WebpackKeys.webpack / WebpackKeys.binary := new File(".") / "node_modules" / ".bin" / "webpack"
Assets / WebpackKeys.webpack / WebpackKeys.configFile := new File(".") / "webpack.config.js"
Assets / WebpackKeys.webpack / WebpackKeys.entries := Map(
  "javascripts/compiled.js" -> Seq(
    "app/assets/javascripts/a.js",
    "app/assets/javascripts/b.js",
    "node_modules/vue/dist/vue.runtime.js",
    "node_modules/axios/dist/axios.js",
    "node_modules/vue-i18n/dist/vue-i18n.js",
  )
)
```

Please see a working example in `test-play-project`.


### 4. Use the generated JS file

You can refer to `javascripts/compiled.js` like it is a normal asset file in Playframework. Here's an example:

```
<script src='@routes.Assets.versioned("javascripts/compiled.js")'></script>
```


Caveats
--------

* It doesn't work correctly with CSS because CSS dependencies are tracked in Webpack's stats. See: https://github.com/GIVESocialMovement/sbt-vuefy/issues/20
