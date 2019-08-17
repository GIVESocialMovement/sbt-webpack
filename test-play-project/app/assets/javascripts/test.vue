<template>
  <div class="example">{{ msg }}</div>
</template>

<script>

  import Dep from './dep.vue'

  export default {
    data () {
      return {
        msg: 'Hello world!'
      }
    }
  }
</script>

<style scoped lang="scss">
  // TODO: tracking css import isn't working yet.
  @import 'test.scss';
  .example {
    color: red;
  }
</style>