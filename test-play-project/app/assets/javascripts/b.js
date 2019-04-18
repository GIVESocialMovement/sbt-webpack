import moment from 'moment';

console.log('hello from b.js');

const currentTime = moment().format('MMMM Do YYYY, h:mm:ss a');

export default currentTime;
