import $ from 'jquery'

import currentTime from './b';

console.log('The current time is', currentTime);

export default $('.time').html(currentTime);
