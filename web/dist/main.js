// Get elements from the DOM
const canvas = document.getElementById('canvas');
const statsEl = document.getElementById('stats');
const ctx = canvas.getContext('2d');
// 1. Display dummy stats (as required by assignment)
const stats = {
    fps: 14.8,
    resolution: "640x480" // Change to your camera's resolution if you know it
};
if (statsEl) {
    statsEl.innerText = `Frame Stats: ${stats.resolution} @ ${stats.fps} FPS`;
}
// 2. Load and display the static processed frame
const img = new Image();
img.src = 'dummy-frame.png';
img.onload = () => {
    if (ctx) {
        // Set canvas size to match image
        canvas.width = img.width;
        canvas.height = img.height;
        // Draw the image
        ctx.drawImage(img, 0, 0);
    }
};
export {};
//# sourceMappingURL=main.js.map