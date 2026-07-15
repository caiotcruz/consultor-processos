module.exports = function(config) {
  config.set({
    basePath:     '',
    frameworks:   ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    client: {
      jasmine: {
        random:  false,
        seed:    42
      },
      clearContext: false
    },
    jasmineHtmlReporter: {
      suppressAll: true
    },
    coverageReporter: {
      dir:     require('path').join(__dirname, './coverage'),
      subdir:  '.',
      reporters: [
        { type: 'html'  },
        { type: 'text-summary' }
      ]
    },
    reporters:   ['progress', 'kjhtml'],
    browsers:    ['ChromeHeadless'],
    customLaunchers: {
      ChromeHeadless: {
        base:  'Chrome',
        flags: ['--no-sandbox', '--headless', '--disable-gpu', '--disable-web-security']
      }
    },
    restartOnFileChange: true,
    singleRun:           false
  });
};