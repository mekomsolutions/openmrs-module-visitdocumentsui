package org.openmrs.module.attachments.obs;

import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.openmrs.Obs;
import org.openmrs.api.APIException;
import org.openmrs.module.attachments.AttachmentsConstants;
import org.openmrs.obs.ComplexData;
import org.openmrs.obs.handler.AbstractHandler;
import org.openmrs.obs.handler.ImageHandler;

public class ImageAttachmentHandler extends AbstractAttachmentHandler {
	
	public final static int THUMBNAIL_MAX_HEIGHT = 200;
	
	public final static int THUMBNAIL_MAX_WIDTH = THUMBNAIL_MAX_HEIGHT;
	
	public ImageAttachmentHandler() {
		super();
	}
	
	@Override
	protected void setParentComplexObsHandler() {
		setParent(new ImageHandler());
	}
	
	@Override
	protected ComplexData readComplexData(Obs obs, ValueComplex valueComplex, String view) {
		
		String fileName = valueComplex.getFileName();
		if (view.equals(AttachmentsConstants.ATT_VIEW_THUMBNAIL)&& 
		  !StringUtils.endsWith(FilenameUtils.removeExtension(fileName),NO_THUMBNAIL_SUFFIX)) {
			fileName = buildThumbnailFileName(fileName);
		}
		
		// We invoke the parent to inherit from the file reading routines.
		Obs tmpObs = new Obs();
		tmpObs.setValueComplex(fileName); // Temp obs used as a safety
		tmpObs = getParent().getObs(tmpObs, AttachmentsConstants.IMAGE_HANDLER_VIEW); // ImageHandler doesn't handle several views
		ComplexData complexData = tmpObs.getComplexData();
		
		// Then we build our own custom complex data
		return getComplexDataHelper().build(valueComplex.getInstructions(), complexData.getTitle(), complexData.getData(),
		    valueComplex.getMimeType()).asComplexData();
	}
	
	@Override
	protected boolean deleteComplexData(Obs obs, AttachmentComplexData complexData) {
		
		// We use a temp obs whose complex data points to the file names
		String fileName = complexData.getTitle();
	    String thumbnailFileName = null;
	    boolean isThumbNailPurged = false;
		Obs tmpObs = new Obs();

	    if (!StringUtils.endsWith(FilenameUtils.removeExtension(fileName), NO_THUMBNAIL_SUFFIX)) {
	    	thumbnailFileName = buildThumbnailFileName(fileName);
			tmpObs.setValueComplex(thumbnailFileName);
			isThumbNailPurged = getParent().purgeComplexData(tmpObs);
	    }
		
		tmpObs.setValueComplex(fileName);
		boolean isImagePurged = getParent().purgeComplexData(tmpObs);
		
		return isThumbNailPurged || isImagePurged;
	}
	
	@Override
	protected ValueComplex saveComplexData(Obs obs, AttachmentComplexData complexData) {
		int imageHeight = Integer.MAX_VALUE;
		int imageWidth = Integer.MAX_VALUE;
		String savedFileName = null;
	
		// We invoke the parent to inherit from the file saving routines.
		obs = getParent().saveObs(obs);

		File savedFile = AbstractHandler.getComplexDataFile(obs);
		// String savedFileName = savedFile.getName();

		// Get image dimensions
		try {
			BufferedImage image = ImageIO.read(savedFile);
			imageHeight = image.getHeight();
			imageWidth = image.getWidth();
		} catch (IOException e) {
			getParent().purgeComplexData(obs);
		         throw new APIException("Can't read the image file"
		               + "OBS_ID='" + obs.getObsId() + "', "
		               + "FILE='" + complexData.getTitle() + "'.", e);
		}

		try {
			savedFileName = saveThumbnailOrRename(savedFile, imageHeight, imageWidth);
		} catch (APIException e) {
			getParent().purgeComplexData(obs);
	        	throw new APIException("A thumbnail file could not be saved for obs with"
	               + "OBS_ID='" + obs.getObsId() + "', "
	               + "FILE='" + complexData.getTitle() + "'.", e);
		}

		return new ValueComplex(complexData.getInstructions(), complexData.getMimeType(), savedFileName);
	}
}
