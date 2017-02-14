attribute vec4 position;
attribute vec4 inputTextureCoordinate;

varying vec2 textureCoordinate;

const mediump float mXStep = 1.0/1000.0;
const mediump float mYStep = 1.0/1000.0;
const int NUM_SAMPLES = 16;
varying vec2 sampleCoordinates[NUM_SAMPLES];

void main()
{
	gl_Position = position;
	textureCoordinate = inputTextureCoordinate.xy;

//	sampleCoordinates[0].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[0].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[1].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[1].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[2].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[2].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[3].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[3].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[4].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[4].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[5].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[5].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[6].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[6].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[7].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[7].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[8].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[8].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[9].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[9].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[10].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[10].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[11].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[11].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[12].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[12].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[13].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[13].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[14].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[14].y = inputTextureCoordinate.x  - mYStep * 10.0;
//
//	sampleCoordinates[15].x = inputTextureCoordinate.x  - mXStep * 10.0;
//	sampleCoordinates[15].y = inputTextureCoordinate.x  - mYStep * 10.0;
}
