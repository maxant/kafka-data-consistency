import { Component, OnInit, NgZone } from '@angular/core';
import { map, delay } from 'rxjs/operators';
import { Observable, Subject } from 'rxjs';
import 'date-carousel/date-carousel.js'

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  count = 0;
  renderObservable$ = new Subject();
  start = this.now();
  adate = this.now(-2);
  adate2 = this.now(-3);

  constructor(private ngZone: NgZone){
  }

  ngOnInit() {
    let btn: {renderObservable} = document.querySelector("#myButton1") as any;
    btn.renderObservable
        .pipe(
          // rendering of the web component is something that happens as a result of angular setting the label
          // attribute in html. that setting seems to happen during the rendering phase. angular doesn't like it
          // when a value is changed during rendering phase, as that would require an additional change detection
          // phase and could lead to infinite loops. in dev mode, it will throw an
          // ExpressionChangedAfterItHasBeenCheckedError. to get around this, we make the change
          // after rendering (using delay as shown here, or setTimeout).
          // adding `delay` causes the error to be surpressed, but also means that the latest result is only shown
          // after the next render phase. so in order to get it to be rendered asap, we make use of ngZone below.
          // because only the changes inside the call to the `run` method are considered for re-rendering, we can't
          // just map or tap, we have to use our own observable and call `next` on it, inside the function passed
          // to the run method. so the code here is not as pretty as it could be. note that timing logs suggest that
          // this call to ngZone costs an extra 10 milliseconds.
          // see:
          // - https://blog.angular-university.io/how-does-angular-2-change-detection-really-work/
          // - https://stackoverflow.com/a/34364881/458370
          // - https://angular.io/api/core/NgZone#description
          // - https://github.com/angular/angular/issues/7381
          // - https://stackoverflow.com/questions/43375532/expressionchangedafterithasbeencheckederror-explained
          // - https://appdividend.com/2018/12/08/angular-7-observables-tutorial-with-example/
          delay(0),
          map(e => `done in ${new Date().getTime()-this.start}ms`),
        ).subscribe(e => {
          this.ngZone.run(() => {
            console.log(e + " done at at " + new Date().toISOString());
            this.renderObservable$.next(e);
          });
        });
  }

  onPressed() {
    this.start = this.now();
    this.count++;
  }

  now(days) {
    return new Date(new Date().getTime() + (days?24*3600000*days:0));
  }

  dateChosen(e) {
    console.log("chosen: " + new Date(e.srcElement.datePicked));
//    this.adate = e.srcElement.datePicked;
  }
}
