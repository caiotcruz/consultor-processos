import { ApplicationConfig, isDevMode, importProvidersFrom } from '@angular/core';
import { provideRouter, withPreloading, PreloadAllModules }   from '@angular/router';
import { provideHttpClient, withInterceptors }                 from '@angular/common/http';
import { provideServiceWorker }                                from '@angular/service-worker';
import { IonicModule }                                         from '@ionic/angular';
import { routes }                                              from './app.routes';
import { jwtInterceptor }                                      from './core/interceptors/jwt.interceptor';
import { errorInterceptor }                                    from './core/interceptors/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withPreloading(PreloadAllModules)),

    provideHttpClient(withInterceptors([jwtInterceptor, errorInterceptor])),

    importProvidersFrom(IonicModule.forRoot({
      mode:             'md',
      backButtonText:   '', 
      swipeBackEnabled: true
    })),

    provideServiceWorker('ngsw-worker.js', {
      enabled:              !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000'
    })
  ]
};