function getFile(content, fileName, mimeType) {
	mimeType = mimeType || 'application/octet-stream';

	// IE10+
	if (navigator.msSaveBlob) {
		return navigator.msSaveBlob(new Blob([ content ], {
			type : mimeType
		}), fileName);
	}

	// Html5 A[download]
	var a = document.createElement('a');
	if ('download' in a) {
		a.href = 'data:' + mimeType + ',' + encodeURIComponent(content);
		a.setAttribute('download', fileName);
		document.body.appendChild(a);
		setTimeout(function() {
			a.click();
			document.body.removeChild(a);
		}, 66);
		return true;
	} else {
		document.body.removeChild(a);
	}

	// Do iframe dataURL download (old Chrome+FF)
	var f = document.createElement('iframe');
	document.body.appendChild(f);
	f.src = 'data:' + mimeType + ',' + encodeURIComponent(content);
	setTimeout(function() {
		document.body.removeChild(f);
	}, 333);
	return true;
};

function getURLParameter(param) {
	var pageURL = window.location.search.substring(1);
	var urlVariables = pageURL.split('&');
	for (var i = 0; i < urlVariables.length; i++) {
		var urlVariable = urlVariables[i].split('=');
		if (urlVariable[0] == param) {
			return urlVariable[1];
		}
	}
	return 'download.log';
}

function download() {
	var content = document.getElementById("logItems").innerHTML;
	// remove html tags
	content = content.replace(/<(?:.|\n)*?>/gm, '');
	// restore encode hmtl symbols like '<', '>' and so on
	content = jQuery('<textarea/>').html(content).text();
	var fileName = getURLParameter('logFile');
	getFile(content, fileName, 'text/plain');
}