<template>
  <div id="app">
    <el-config-provider :locale="locale">
      <router-view />
    </el-config-provider>
    <ToastContainer />
  </div>
</template>

<script setup lang="ts">
import { ElConfigProvider } from 'element-plus'
import { ToastContainer } from 'vue3-toastify'
import en from 'element-plus/dist/locale/en.mjs'

const locale = en
</script>

<style lang="scss">
#app {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  height: 100vh;
  overflow-x: hidden;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  padding: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  min-height: 100vh;
}

// Custom scrollbar
::-webkit-scrollbar {
  width: 8px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

// Dark mode scrollbar
.dark {
  ::-webkit-scrollbar-track {
    background: #2d3748;
  }
  
  ::-webkit-scrollbar-thumb {
    background: #4a5568;
  }
  
  ::-webkit-scrollbar-thumb:hover {
    background: #718096;
  }
}
</style>
