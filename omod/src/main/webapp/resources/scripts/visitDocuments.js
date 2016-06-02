angular.module('vdui.widget.dropzone', []).directive('dropzoneDirective',
  function () {
    return function (scope, element, attrs) {
      var config, dropzone;

      config = scope[attrs.dropzoneDirective];

      // create a Dropzone for the element with the given options
      dropzone = new Dropzone(element[0], config.options);

      scope.processDropzone = function() {
        dropzone.processQueue();
      };

      scope.removeAllFiles = function() {
        dropzone.removeAllFiles();
      };

      // bind the given event handlers
      angular.forEach(config.eventHandlers, function(handler, event) {
        dropzone.on(event, handler);
      });
    };
  });

angular.module('vdui.page.main', ['vdui.widget.dropzone', 'obsService', 'session', 'vdui.widget.thumbnail', 'vdui.widget.modalImage']);

angular.module('vdui.page.main').controller('FileUploadCtrl', ['$scope', '$window', 'SessionInfo',
  function ($scope, $window, SessionInfo) {

    var providerUuid = "";
    $scope.init = function() {
      // This happens on page load, by the time any file is dropped in, the provider would have been fetched.
      SessionInfo.get().$promise.then(function(info) {
        providerUuid = info.currentProvider.uuid;
      });
    }

    $scope.dropzoneConfig = {
      
      'options': // passed into the Dropzone constructor
      { 
        'url': $window.config.uploadUrl,
        'paramName': 'visit_document_file',
        'maxFiles': 1,
        'maxFilesize': $window.config.maxFileSize,
        'acceptedFiles': 'image/*', 
        'autoProcessQueue': false
      },
      'eventHandlers':
      {
        'addedfile': function(file) {
          $scope.file = file;
          if (this.files[1] != null) {
            this.removeFile(this.files[0]);
          }
          $scope.$apply(function() {
            $scope.fileAdded = true;
          });
        },
        'sending': function (file, xhr, formData) {
          formData.append('provider', providerUuid);
          formData.append('fileCaption', $scope.fileCaption);
        },
        'success': function (file, response) {
          $scope.$broadcast('vdui_event_newComplexObs', response);
          $scope.clearForms();
        }
      }
    };

    $scope.uploadFile = function() {
      $scope.processDropzone();
    };

    $scope.clearForms = function() {
      $scope.removeAllFiles();
      $scope.fileCaption = "";
    }

  }]);

angular.module('vdui.page.main').controller('ListComplexObsCtrl', ['$scope', '$window', 'ObsService',
  function($scope, $window, ObsService) {

    ObsService.getObs({
      patient: $window.config.patient.uuid,
      concept: $window.config.conceptComplexUuid,
      v: $window.config.obsRep
    }).then(function(obs) {
      $scope.obsArray = obs;
    })

    $scope.$on('vdui_event_newComplexObs', function(event, obs) {
      $scope.obsArray.unshift(obs);
      $scope.$apply();
    });

    // Setting the config for the thumbnail directive
    $scope.thumbnailCfg = {};
    $scope.thumbnailCfg.url = $window.config.downloadUrl + '?'
        + 'view=' + $window.config.thumbView + '&'
        + 'obs=';
    $scope.thumbnailCfg.afterUrl = $window.config.downloadUrl + '?'
        + 'view=' + $window.config.originalView + '&'
        + 'obs=';

  }]);