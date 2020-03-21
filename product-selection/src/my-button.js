// this is library code - its part of the view and is decoupled from the model using events.
// it knows nothing about the business model at all. but it does perhaps have it's own model.

// example taken from https://www.robinwieruch.de/web-components-tutorial

/**
 * An RxJS backed button.
 *
 * Properties: "label" => gets/sets the attribute "label"
 * Attributes: "label" => the text shown by this button
 * Events: "mypressed" => emitted when the thing is clicked
 * Definition: "my-button"
 *
 * Example:
 *
 * <my-button id="aBtn"></my-button>
 *
 * const btn = document.querySelector('#aBtn');
 * rxjs.fromEvent(btn, 'mypressed')
 *     .subscribe(val => {
 *         ... do something...
 *     });
 *
 * // This also works:
 * btn.addEventListener('mypressed', value => {
 *     ... do something...
 * });
 *
 * you can also add a model, which can be used with some moustache type cleverness to declare the contents:
 *
 * <my-button>{{data.value}}</my-button>
 * button.model = { data: { value: "(click to increment)" } };
 *
 * This is refreshed every time the button is redrawn (when its attributes change).
 */
const template = document.createElement('template');
template.innerHTML = `
  <style>
    :host[italic] {
        font-style: italic;
    }

    .container {
      padding: 8px;
      border: 1px solid green;
    }

    button {
      display: block;
      overflow: hidden;
      position: relative;
      padding: 0 16px;
      font-size: 16px;
      font-weight: bold;
      text-overflow: ellipsis;
      white-space: nowrap;
      cursor: pointer;
      outline: none;
      width: 200px;
      height: 40px;
      box-sizing: border-box;
      border: 1px solid #a1a1a1;
      background: var(--my-button-background-color, orange); /*orange is the default */
      box-shadow: 0 2px 4px 0 rgba(0,0,0, 0.05), 0 2px 8px 0 rgba(161,161,161, 0.4);
      color: #363636;
      cursor: pointer;
    }

    :host {
        border: 1px solid red; /* looks weird, but seems unrelated to css. not sure why. we just want to put a box around the component. can do that with an outer div too. */
    }
  </style>
  <div class="container">
    <button></button>
    <slot></slot>
    <ul id="times">
    </ul>
  </div>
`;
class Button extends HTMLElement {
  times = [];
  renderObservable;
  constructor() {
    super();
    this._shadowRoot = this.attachShadow({ mode: 'open' }); // closed => closed means you cannot access it using eg. document.querySelector? well cant do that with open anyway
    this._shadowRoot.appendChild(template.content.cloneNode(true));

    this.$button = this._shadowRoot.querySelector('button'); // the button that gets clicked
    this.$times  = this._shadowRoot.querySelector('#times'); // a list of times when the button was clicked

    // hook up events to custom event which this component emits
    this.$button.addEventListener('click', () => {
      this.times.unshift(new Date());
      this.dispatchEvent(new CustomEvent('mypressed', {}));
    });

    this.renderObservable = new rxjs.Subject();
  }
  get label() {
    return this.getAttribute('label');
  }
  set label(value) {
    this.setAttribute('label', value);
    // by having this setter, we can also do this, somewhere else in our code:
    //      const element = document.querySelector('my-button');
    //      element.label = 'Click Me';
    // without the setter, it wouldnt work because the above is setting a property, not an attribute of the html!
  }
  get mystyle() {
    return this.getAttribute('mystyle');
  }
  set mystyle(value) {
console.log("setting mystyle: " + value);
    this.setAttribute('mystyle', value);
  }
  static get observedAttributes() {
    return ['label', 'mystyle'];
  }
  connectedCallback() {
console.log(`my-button connected callback`);
    if (this.hasAttribute('as-atom')) { // this is how to query an attributes existence. attributes are found in the html
    }
  }
  disconnectedCallback() {
console.log(`my-button disconnected callback`);
  }
  adoptedCallback() {
console.log(`my-button adopted callback`);
  }
  attributeChangedCallback(name, oldVal, newVal) {
console.log(`my-button changed callback: ${name} from '${oldVal}' to '${newVal}'`);

    // because there's a getter, we don't need to do this:
    //      this[name] = newVal;

    if(name == 'mystyle') {
        newVal.split(';').forEach(s => {
            let kv = s.trim().split(':');
            if(kv[0].trim().length) {
                this.$button.style[kv[0].trim()] = kv[1].trim();
            }
        });
    }

    this.render();
  }
  render() {
    this.$button.innerHTML = this.label;
    let s = "";
    this.times.forEach(t => {
        s += `<li>${t}</li>`; // string manipulation is many times faster than eg. appendChild
    });
    this.$times.innerHTML = s;
    this.renderObservable.next();
  }
}
window.customElements.define('my-button', Button);
